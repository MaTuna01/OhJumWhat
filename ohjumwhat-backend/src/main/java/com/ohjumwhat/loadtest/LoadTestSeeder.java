package com.ohjumwhat.loadtest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.chat.ChatPhotoStorage;
import com.ohjumwhat.common.TimeConfig;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.organization.Membership;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.Organization;
import com.ohjumwhat.organization.OrganizationRepository;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.user.ProfilePhotoStorage;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteRepository;

/**
 * 부하 테스트 데이터를 만들고 지운다. {@link LoadTestRunner}(loadtest 프로필)가 부르고, 통합 테스트도 직접 부른다.
 *
 * <p>시드: 조직마다 회원 n명·멤버십·오늘 진행 중인 투표 1개(메뉴 4개, 일부 미리 참여)와 회원마다 로그인 세션 1개를 만든다.
 * 세션은 실제 구글 로그인과 같은 모양(SPRING_SECURITY_CONTEXT에 OAuth2AuthenticationToken + LoginUser)으로
 * spring_session에 저장하므로, 부하 도구가 SESSION 쿠키만 보내면 /api/**를 쓸 수 있다. 같은 모양을 만드는 테스트
 * (ChatSocketTest.sessionCookie)와 함께 고친다.
 *
 * <p>정리: {@link LoadTestNames}의 접두어로 찾은 회원·조직·세션만 지운다. 접두어 조직에 실제 회원이 들어와 있으면
 * 그 조직은 건너뛰고 알린다(그 조직의 테스트 회원만 빠진다).
 */
@Slf4j
@Service
public class LoadTestSeeder {

	/** 투표마다 두는 메뉴. (poll, name) UNIQUE라 서로 달라야 한다. */
	static final List<String> MENU_NAMES = List.of("김치찌개", "제육볶음", "돈까스", "파스타");

	/** 지난 투표에 돌아가며 넣는 메뉴 이름. 띄어쓰기만 다른 이름을 섞어 통계가 같은 메뉴로 묶는 경우도 만든다. */
	static final List<String> HISTORY_MENUS = List.of("김치찌개", "된장찌개", "제육볶음", "돈까스", "파스타", "초밥", "쌀국수",
			"칼국수", "냉면", "비빔밥", "순두부찌개", "부대찌개", "치킨", "피자", "햄버거", "떡볶이", "샐러드", "샌드위치", "마라탕", "짜장면",
			"짬뽕", "탕수육", "갈비탕", "김치 찌개");

	/** 지난 투표의 오픈·마감 시각(한국) */
	static final LocalTime HISTORY_OPENS = LocalTime.of(11, 0);

	static final LocalTime HISTORY_CLOSES = LocalTime.of(12, 0);

	/** 세션 유효 기간. 정리를 잊어도 Spring Session의 만료 정리가 지운다. */
	static final Duration SESSION_TTL = Duration.ofHours(12);

	/**
	 * @param orgs 조직 수
	 * @param members 조직당 멤버 수
	 * @param closesAt 모든 투표의 마감 시각(지금보다 뒤)
	 * @param prevote 미리 참여시킬 멤버 비율(0~1). 참여자는 메뉴를 돌아가며 고른다
	 * @param history 조직마다 만들 지난 투표 수(어제부터 하루에 하나, 11:00~12:00, 메뉴 4개, 멤버 전원 참여)
	 */
	public record SeedSpec(int orgs, int members, Instant closesAt, double prevote, int history) {

		public SeedSpec(int orgs, int members, Instant closesAt, double prevote) {
			this(orgs, members, closesAt, prevote, 0);
		}
	}

	/**
	 * @param skippedOrgIds 실제 회원이 섞여 있어 지우지 않은 접두어 조직
	 */
	public record CleanResult(int users, int organizations, int sessions, int photoFiles, List<Long> skippedOrgIds) {
	}

	private final UserRepository userRepository;

	private final OrganizationRepository organizationRepository;

	private final MembershipRepository membershipRepository;

	private final PollRepository pollRepository;

	private final MenuOptionRepository menuOptionRepository;

	private final VoteRepository voteRepository;

	private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	private final JdbcTemplate jdbcTemplate;

	private final NamedParameterJdbcTemplate namedJdbc;

	private final TransactionTemplate transaction;

	private final ProfilePhotoStorage profilePhotoStorage;

	private final ChatPhotoStorage chatPhotoStorage;

	private final Clock clock;

	public LoadTestSeeder(UserRepository userRepository, OrganizationRepository organizationRepository,
			MembershipRepository membershipRepository, PollRepository pollRepository,
			MenuOptionRepository menuOptionRepository, VoteRepository voteRepository,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository, JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager, ProfilePhotoStorage profilePhotoStorage,
			ChatPhotoStorage chatPhotoStorage, Clock clock) {
		this.userRepository = userRepository;
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.pollRepository = pollRepository;
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.sessionRepository = sessionRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
		this.transaction = new TransactionTemplate(transactionManager);
		this.profilePhotoStorage = profilePhotoStorage;
		this.chatPhotoStorage = chatPhotoStorage;
		this.clock = clock;
	}

	/** 부하 테스트 회원이나 조직이 하나라도 남아 있는지 */
	public boolean exists() {
		Boolean found = jdbcTemplate.queryForObject("""
				select exists (select 1 from users where google_sub like ? and email like ?)
					or exists (select 1 from organizations where name like ?)""", Boolean.class,
				LoadTestNames.subPattern(), LoadTestNames.emailPattern(), LoadTestNames.organizationPattern());
		return Boolean.TRUE.equals(found);
	}

	/**
	 * 조직 단위 트랜잭션으로 만든다(조직 50개 × 회원 20명을 한 트랜잭션에 넣지 않는다). 이미 시드 데이터가 있으면 거절한다.
	 *
	 * @throws IllegalStateException 시드 데이터가 남아 있을 때(먼저 정리한다)
	 */
	public SeedResult seed(SeedSpec spec) {
		Instant now = Instant.now(clock);
		if (!spec.closesAt().isAfter(now)) {
			throw new IllegalArgumentException("마감 시각은 지금보다 뒤여야 합니다.");
		}
		if (exists()) {
			throw new IllegalStateException("부하 테스트 데이터가 남아 있습니다. 먼저 clean을 실행해 주세요.");
		}
		List<SeedResult.Org> orgs = new ArrayList<>();
		for (int org = 1; org <= spec.orgs(); org++) {
			int number = org;
			orgs.add(transaction.execute(status -> seedOrganization(number, spec, now)));
			if (org % 10 == 0 || org == spec.orgs()) {
				log.info("시드 진행: 조직 {}/{}", org, spec.orgs());
			}
		}
		SeedResult result = new SeedResult(now, spec.closesAt(), orgs);
		log.info("시드 완료: 조직 {}개, 회원 {}명, 마감 {}", orgs.size(), result.memberCount(), spec.closesAt());
		return result;
	}

	private SeedResult.Org seedOrganization(int org, SeedSpec spec, Instant now) {
		List<User> fresh = new ArrayList<>();
		for (int member = 1; member <= spec.members(); member++) {
			User user = new User(LoadTestNames.googleSub(org, member), LoadTestNames.email(org, member),
					LoadTestNames.userName(org, member), null);
			user.recordLogin(now);
			fresh.add(user);
		}
		List<User> users = userRepository.saveAll(fresh);
		Long creatorId = users.getFirst().getId();

		Organization organization = organizationRepository
			.save(new Organization(LoadTestNames.organizationName(org), LoadTestNames.inviteToken()));
		membershipRepository.saveAll(users.stream()
			.map(user -> new Membership(organization.getId(), user.getId(), now))
			.toList());

		LocalDate today = LocalDate.ofInstant(now, TimeConfig.KST);
		Poll poll = pollRepository.save(Poll.manual(organization.getId(), creatorId,
				LoadTestNames.POLL_PREFIX + "점심", today, now, spec.closesAt()));
		List<MenuOption> options = menuOptionRepository.saveAll(MENU_NAMES.stream()
			.map(name -> new MenuOption(poll.getId(), creatorId, name))
			.toList());

		int voters = (int) Math.round(users.size() * spec.prevote());
		for (int i = 0; i < voters; i++) {
			voteRepository.upsert(poll.getId(), users.get(i).getId(), options.get(i % options.size()).getId());
		}

		if (spec.history() > 0) {
			seedHistory(organization.getId(), creatorId, users.stream().map(User::getId).toList(), spec.history(), today);
		}

		List<SeedResult.Member> members = users.stream()
			.map(user -> new SeedResult.Member(user.getId(), user.getGoogleSub(), sessionCookie(user)))
			.toList();
		return new SeedResult.Org(organization.getId(), organization.getName(), poll.getId(),
				options.stream().map(MenuOption::getId).toList(), members);
	}

	/**
	 * 지난 투표를 JDBC 배치로 넣는다(조직 50개 × 500일이면 엔티티 저장은 너무 느리다). 날짜마다 메뉴 4개를 이름 풀에서 돌아가며 고르고,
	 * 멤버 전원이 (멤버 번호 + 날짜) 순서로 메뉴를 고른다. 마감된 투표에 참여자가 있어 통계·추천·자동완성·랭킹의 대상이 된다.
	 */
	private void seedHistory(Long orgId, Long creatorId, List<Long> userIds, int days, LocalDate today) {
		List<Object[]> polls = new ArrayList<>();
		for (int d = 1; d <= days; d++) {
			LocalDate date = today.minusDays(d);
			polls.add(new Object[] { orgId, creatorId, LoadTestNames.POLL_PREFIX + "점심", date, offset(date, HISTORY_OPENS),
					offset(date, HISTORY_CLOSES) });
		}
		jdbcTemplate.batchUpdate("""
				insert into polls (organization_id, created_by, title, poll_date, opens_at, closes_at)
				values (?, ?, ?, ?, ?, ?)""", polls);
		List<Map<String, Object>> pollRows = jdbcTemplate.queryForList(
				"select id, poll_date from polls where organization_id = ? and poll_date < ? order by poll_date desc", orgId,
				today);

		List<Object[]> options = new ArrayList<>();
		List<Long> pollIds = new ArrayList<>();
		int d = 0;
		for (Map<String, Object> row : pollRows) {
			d++;
			long pollId = ((Number) row.get("id")).longValue();
			pollIds.add(pollId);
			for (int i = 0; i < 4; i++) {
				options.add(new Object[] { pollId, creatorId, HISTORY_MENUS.get((d * 4 + i) % HISTORY_MENUS.size()) });
			}
		}
		jdbcTemplate.batchUpdate("insert into menu_options (poll_id, created_by, name) values (?, ?, ?)", options);
		Map<Long, List<Long>> optionsByPoll = new LinkedHashMap<>();
		namedJdbc.queryForList("select id, poll_id from menu_options where poll_id in (:ids) order by poll_id, id",
				Map.of("ids", pollIds))
			.forEach(row -> optionsByPoll.computeIfAbsent(((Number) row.get("poll_id")).longValue(), id -> new ArrayList<>())
				.add(((Number) row.get("id")).longValue()));

		List<Object[]> votes = new ArrayList<>();
		d = 0;
		for (Map.Entry<Long, List<Long>> entry : optionsByPoll.entrySet()) {
			d++;
			List<Long> optionIds = entry.getValue();
			for (int m = 0; m < userIds.size(); m++) {
				votes.add(new Object[] { entry.getKey(), userIds.get(m), optionIds.get((m + d) % optionIds.size()) });
			}
		}
		jdbcTemplate.batchUpdate("insert into votes (poll_id, user_id, option_id) values (?, ?, ?)", votes);
	}

	private static OffsetDateTime offset(LocalDate date, LocalTime time) {
		return ZonedDateTime.of(date, time, TimeConfig.KST).toInstant().atOffset(ZoneOffset.UTC);
	}

	/** 그 회원으로 구글 로그인한 세션을 저장하고 브라우저가 보낼 SESSION 쿠키 값을 만든다. */
	private String sessionCookie(User user) {
		OidcIdToken idToken = OidcIdToken.withTokenValue("loadtest")
			.subject(user.getGoogleSub())
			.claim("email", user.getEmail())
			.issuedAt(Instant.now(clock))
			.expiresAt(Instant.now(clock).plus(SESSION_TTL))
			.build();
		LoginUser loginUser = new LoginUser(user.getId(),
				new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken));
		OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(loginUser,
				loginUser.getAuthorities(), "google");
		String id = saveSession(sessionRepository, new SecurityContextImpl(authentication), user.getGoogleSub());
		return Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
	}

	private static <S extends Session> String saveSession(FindByIndexNameSessionRepository<S> repository,
			SecurityContextImpl context, String principalName) {
		S session = repository.createSession();
		session.setMaxInactiveInterval(SESSION_TTL);
		session.setAttribute("SPRING_SECURITY_CONTEXT", context);
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
		repository.save(session);
		return session.getId();
	}

	/**
	 * 접두어로 찾은 세션 → 조직(CASCADE로 투표·메뉴·응답·채팅·멤버십) → 회원 순서로 한 트랜잭션에서 지우고,
	 * 커밋한 뒤 올린 프로필 사진·채팅 사진 파일을 지운다. 실제 회원이 멤버로 있는 접두어 조직은 두고 알린다.
	 */
	public CleanResult clean() {
		String sub = LoadTestNames.subPattern();
		String email = LoadTestNames.emailPattern();
		String orgName = LoadTestNames.organizationPattern();
		List<Long> userIds = jdbcTemplate.queryForList("select id from users where google_sub like ? and email like ?",
				Long.class, sub, email);
		List<Long> candidateOrgIds = jdbcTemplate.queryForList("select id from organizations where name like ?",
				Long.class, orgName);
		List<Long> orgIds = jdbcTemplate.queryForList("""
				select o.id from organizations o
				where o.name like ?
					and not exists (
						select 1 from memberships m join users u on u.id = m.user_id
						where m.organization_id = o.id and not (u.google_sub like ? and u.email like ?))""", Long.class,
				orgName, sub, email);
		List<Long> skipped = candidateOrgIds.stream().filter(id -> !orgIds.contains(id)).toList();
		if (!skipped.isEmpty()) {
			log.warn("실제 회원이 섞여 있어 지우지 않는 조직: {}", skipped);
		}

		List<String> profilePhotoKeys = userIds.isEmpty() ? List.of() : namedJdbc.queryForList(
				"select photo_key from users where id in (:ids) and photo_key is not null", Map.of("ids", userIds),
				String.class);
		List<String> chatPhotoKeys = orgIds.isEmpty() ? List.of() : namedJdbc.queryForList("""
				select m.image_key from chat_messages m
				where m.image_key is not null
					and m.poll_id in (select p.id from polls p where p.organization_id in (:ids))""",
				Map.of("ids", orgIds), String.class);

		int[] deleted = transaction.execute(status -> {
			int sessions = jdbcTemplate.update("delete from spring_session where principal_name like ?", sub);
			int organizations = orgIds.isEmpty() ? 0
					: namedJdbc.update("delete from organizations where id in (:ids)", Map.of("ids", orgIds));
			int users = userIds.isEmpty() ? 0
					: namedJdbc.update("delete from users where id in (:ids)", Map.of("ids", userIds));
			return new int[] { users, organizations, sessions };
		});

		profilePhotoKeys.forEach(profilePhotoStorage::delete);
		chatPhotoKeys.forEach(chatPhotoStorage::delete);
		CleanResult result = new CleanResult(deleted[0], deleted[1], deleted[2],
				profilePhotoKeys.size() + chatPhotoKeys.size(), skipped);
		log.info("정리 완료: 회원 {}명, 조직 {}개, 세션 {}개, 사진 파일 {}개, 건너뛴 조직 {}", result.users(),
				result.organizations(), result.sessions(), result.photoFiles(), skipped);
		return result;
	}
}
