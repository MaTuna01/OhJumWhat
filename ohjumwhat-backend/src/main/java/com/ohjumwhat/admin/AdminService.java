package com.ohjumwhat.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.guestbook.GuestbookReportRepository;
import com.ohjumwhat.guestbook.GuestbookService;
import com.ohjumwhat.letter.LetterReportRepository;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.organization.LeaveResponse;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.Organization;
import com.ohjumwhat.organization.OrganizationDeletedEvent;
import com.ohjumwhat.organization.OrganizationRepository;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollDeletedEvent;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.poll.PollStatus;
import com.ohjumwhat.sanction.UserSanctionRepository;
import com.ohjumwhat.schedule.PollSchedule;
import com.ohjumwhat.schedule.PollScheduleRepository;
import com.ohjumwhat.schedule.ScheduleResponse;
import com.ohjumwhat.user.BlockedAccount;
import com.ohjumwhat.user.BlockedAccountRepository;
import com.ohjumwhat.user.ProfilePhotoStorage;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.user.UserService;
import com.ohjumwhat.vote.VoteRepository;

/**
 * 관리자 콘솔: 서비스 전체 조회와 강제 삭제. 권한 확인은 SecurityConfig(AdminAuthorizationManager)가 한다.
 * 지우는 작업은 모두 관리자 ID와 대상 ID를 로그로 남긴다.
 */
@Slf4j
@Service
public class AdminService {

	private static final int LIST_LIMIT = 100;

	private static final int RECENT_POLL_LIMIT = 30;

	private static final String USER_NOT_FOUND = "회원을 찾을 수 없어요.";

	private static final String ORGANIZATION_NOT_FOUND = "조직을 찾을 수 없어요.";

	private static final String POLL_NOT_FOUND = "투표를 찾을 수 없어요.";

	private final AdminRepository adminRepository;

	private final UserRepository userRepository;

	private final UserService userService;

	private final BlockedAccountRepository blockedAccountRepository;

	private final OrganizationRepository organizationRepository;

	private final MembershipRepository membershipRepository;

	private final OrganizationService organizationService;

	private final PollRepository pollRepository;

	private final PollService pollService;

	private final MenuOptionRepository menuOptionRepository;

	private final VoteRepository voteRepository;

	private final PollScheduleRepository scheduleRepository;

	private final JdbcTemplate jdbcTemplate;

	private final ProfilePhotoStorage photoStorage;

	private final LetterReportRepository letterReportRepository;

	private final GuestbookReportRepository guestbookReportRepository;

	private final GuestbookService guestbookService;

	private final UserSanctionRepository sanctionRepository;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public AdminService(AdminRepository adminRepository, UserRepository userRepository, UserService userService,
			BlockedAccountRepository blockedAccountRepository, OrganizationRepository organizationRepository,
			MembershipRepository membershipRepository, OrganizationService organizationService,
			PollRepository pollRepository, PollService pollService, MenuOptionRepository menuOptionRepository,
			VoteRepository voteRepository, PollScheduleRepository scheduleRepository, JdbcTemplate jdbcTemplate,
			ProfilePhotoStorage photoStorage, LetterReportRepository letterReportRepository,
			GuestbookReportRepository guestbookReportRepository, GuestbookService guestbookService,
			UserSanctionRepository sanctionRepository, ApplicationEventPublisher events, Clock clock) {
		this.adminRepository = adminRepository;
		this.userRepository = userRepository;
		this.userService = userService;
		this.blockedAccountRepository = blockedAccountRepository;
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.organizationService = organizationService;
		this.pollRepository = pollRepository;
		this.pollService = pollService;
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.scheduleRepository = scheduleRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.photoStorage = photoStorage;
		this.letterReportRepository = letterReportRepository;
		this.guestbookReportRepository = guestbookReportRepository;
		this.guestbookService = guestbookService;
		this.sanctionRepository = sanctionRepository;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AdminResponses.Stats stats() {
		Instant now = Instant.now(clock);
		long openLetterReports = letterReportRepository.countOpen();
		long openGuestbookReports = guestbookReportRepository.countOpen();
		return new AdminResponses.Stats(adminRepository.countUsers(), adminRepository.countOrganizations(),
				adminRepository.countPollsOn(LocalDate.now(clock)), adminRepository.countOpenPolls(now),
				adminRepository.countUsersSince(now.minus(7, ChronoUnit.DAYS)), adminRepository.countBlocks(),
				openLetterReports + openGuestbookReports, openLetterReports, openGuestbookReports,
				sanctionRepository.countRestrictedUsers(now));
	}

	// 회원

	@Transactional(readOnly = true)
	public List<AdminResponses.UserRow> users(String q) {
		return withRestricted(adminRepository.findUsers(keyword(q), PageRequest.of(0, LIST_LIMIT)));
	}

	@Transactional(readOnly = true)
	public AdminResponses.UserDetail user(Long userId) {
		AdminResponses.UserRow row = adminRepository.findUser(userId)
			.map(found -> withRestricted(List.of(found)).get(0))
			.orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		User user = userRepository.findById(userId).orElseThrow();
		// 세션(spring_session, Flyway V2)의 마지막 요청 시각. principal 이름은 google sub다(LoginUser).
		Long lastAccess = jdbcTemplate.queryForObject(
				"select max(last_access_time) from spring_session where principal_name = ?", Long.class,
				user.getGoogleSub());
		AdminResponses.Activity activity = new AdminResponses.Activity(adminRepository.countPollsCreatedBy(userId),
				adminRepository.countMenusAddedBy(userId), adminRepository.countResponsesBy(userId));
		return new AdminResponses.UserDetail(row, user.getNickname(), user.getBio(), user.getFoodTags(),
				user.getDetails(), lastAccess == null ? null : Instant.ofEpochMilli(lastAccess),
				adminRepository.findOrganizationsOfUser(userId), activity);
	}

	/**
	 * 강제 탈퇴: 모든 조직에서 탈퇴(마지막 멤버였던 조직은 삭제) → 같은 구글 계정 차단 → 회원 삭제 → 로그인 세션 만료.
	 * 회원을 지우면 그 사람의 응답은 모두 지워지고(CASCADE), 올린 메뉴는 작성자만 비운 채 남는다(SET NULL).
	 * 올린 프로필 사진 파일은 커밋한 뒤에 지운다. 그 사람이 보낸 쪽지의 열린 신고는 처리 완료로 한다(지우면 보낸 사람을 알 수 없다).
	 * 그 사람이 쓴 방명록 글의 처리 전 신고도 「글 제한」으로 처리하고 그 글을 제한한다. 글은 쓴 사람만 비운 채(「탈퇴한 사용자」)
	 * 남고, 그 사람의 방명록에 남은 글과 신고도 주인만 비운 채 남는다.
	 */
	@Transactional
	public void withdraw(Long adminId, Long userId) {
		User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		if (user.getId().equals(adminId)) {
			throw ApiException.conflict("자기 자신은 탈퇴시킬 수 없어요.");
		}
		if (user.isAdmin()) {
			throw ApiException.conflict("관리자는 탈퇴시킬 수 없어요. 먼저 서버 설정에서 관리자를 해제해 주세요.");
		}
		int deletedOrganizations = 0;
		for (Long organizationId : membershipRepository.findOrganizationIdsByUserId(userId)) {
			if (organizationService.removeMember(organizationId, userId)) {
				deletedOrganizations++;
			}
		}
		int resolvedReports = letterReportRepository.resolveOpenAgainst(userId, adminId, Instant.now(clock));
		int restrictedGuestbookReports = guestbookService.restrictOpenReportsAgainst(userId, adminId);
		BlockedAccount block = blockedAccountRepository.save(new BlockedAccount(user, adminId, Instant.now(clock)));
		photoStorage.deleteAfterCommit(user.getPhotoKey());
		userRepository.delete(user);
		userRepository.flush();
		// 세션 저장소 API는 별도 트랜잭션으로 커밋되므로, 같은 트랜잭션에서 지우도록 SQL을 쓴다.
		int expiredSessions = jdbcTemplate.update("delete from spring_session where principal_name = ?",
				block.getGoogleSub());
		log.info("관리자 강제 탈퇴: adminId={}, userId={}, blockId={}, 삭제된 조직 수={}, 만료한 세션 수={}, 처리한 쪽지 신고 수={}, "
				+ "글 제한으로 처리한 방명록 신고 수={}", adminId, userId, block.getId(), deletedOrganizations, expiredSessions,
				resolvedReports, restrictedGuestbookReports);
	}

	/** 올린 프로필 사진 지우기(부적절한 사진 대응). 구글 사진으로 돌아가고, 파일은 커밋한 뒤에 지운다. 올린 사진이 없으면 그대로 둔다. */
	@Transactional
	public void deleteUserPhoto(Long adminId, Long userId) {
		User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		String photoKey = user.getPhotoKey();
		if (photoKey == null) {
			return;
		}
		userRepository.updatePhotoKey(userId, null);
		photoStorage.deleteAfterCommit(photoKey);
		log.info("관리자 프로필 사진 삭제: adminId={}, userId={}", adminId, userId);
	}

	/*
	 * 프로필 수정(별명·소개·상세 프로필): 부적절한 프로필 대응. 마이페이지와 같은 규칙·같은 문구로 UserService가 정리해 저장하고,
	 * 본인에게 따로 알리지 않는다(사진 지우기처럼). 다른 관리자와 자기 자신도 고칠 수 있다. 사진 지우기처럼 회원 행을 잠가
	 * 같은 회원의 강제 탈퇴와 겹치지 않게 하고, 바뀐 회원 상세(GET /users/{id}와 같은 응답)를 돌려준다. 로그에 값은 남기지 않는다.
	 */

	/** 별명 바꾸기. 비우면 별명을 지우고 구글 이름으로 돌아간다(구글 이름은 로그인 때마다 바뀌어 고칠 수 없다). */
	@Transactional
	public AdminResponses.UserDetail changeNickname(Long adminId, Long userId, String rawNickname) {
		lockUser(userId);
		userService.saveNickname(userId, rawNickname);
		AdminResponses.UserDetail detail = user(userId);
		log.info("관리자 별명 변경: adminId={}, userId={}, 별명 있음={}", adminId, userId, detail.nickname() != null);
		return detail;
	}

	/** 한줄 소개와 좋아하는 음식 바꾸기(통째로 바꾼다, 비우면 지운다). */
	@Transactional
	public AdminResponses.UserDetail changeIntro(Long adminId, Long userId, String rawBio, List<String> rawFoodTags) {
		lockUser(userId);
		userService.saveIntro(userId, rawBio, rawFoodTags);
		AdminResponses.UserDetail detail = user(userId);
		log.info("관리자 프로필 소개 변경: adminId={}, userId={}, 소개 있음={}, 음식 {}개", adminId, userId,
				detail.bio() != null, detail.foodTags().size());
		return detail;
	}

	/** 상세 프로필 바꾸기. 마이페이지처럼 다섯 항목 모두 필수다(DB CHECK: 모두 비었거나 모두 채워졌거나). */
	@Transactional
	public AdminResponses.UserDetail changeDetails(Long adminId, Long userId, String rawMbti, String rawPersonalColor,
			List<String> rawHobbies, Integer rawAge, String rawJobTitle) {
		lockUser(userId);
		userService.saveDetails(userId, rawMbti, rawPersonalColor, rawHobbies, rawAge, rawJobTitle);
		log.info("관리자 상세 프로필 변경: adminId={}, userId={}", adminId, userId);
		return user(userId);
	}

	/** 상세 프로필 지우기: 다섯 항목을 한꺼번에 비운다. 본인이 다음에 들어오면 다시 채우기 안내를 본다. 비어 있으면 그대로 둔다. */
	@Transactional
	public AdminResponses.UserDetail clearDetails(Long adminId, Long userId) {
		User user = lockUser(userId);
		if (user.getDetails() != null) {
			userRepository.clearDetails(userId);
			log.info("관리자 상세 프로필 삭제: adminId={}, userId={}", adminId, userId);
		}
		return user(userId);
	}

	@Transactional(readOnly = true)
	public List<AdminResponses.Block> blocks() {
		return adminRepository.findBlocks();
	}

	/** 차단 해제: 같은 구글 계정으로 다시 로그인하면 새 회원으로 가입된다. */
	@Transactional
	public void unblock(Long adminId, Long blockId) {
		BlockedAccount block = blockedAccountRepository.findById(blockId)
			.orElseThrow(() -> ApiException.notFound("차단 기록을 찾을 수 없어요."));
		blockedAccountRepository.delete(block);
		log.info("관리자 차단 해제: adminId={}, blockId={}", adminId, blockId);
	}

	// 조직

	@Transactional(readOnly = true)
	public List<AdminResponses.OrganizationRow> organizations(String q) {
		return adminRepository.findOrganizations(keyword(q), PageRequest.of(0, LIST_LIMIT));
	}

	@Transactional(readOnly = true)
	public AdminResponses.OrganizationDetail organization(Long organizationId) {
		AdminResponses.OrganizationRow row = adminRepository.findOrganization(organizationId)
			.orElseThrow(() -> ApiException.notFound(ORGANIZATION_NOT_FOUND));
		Instant now = Instant.now(clock);
		List<AdminResponses.PollRow> polls = adminRepository
			.findRecentPolls(organizationId, PageRequest.of(0, RECENT_POLL_LIMIT))
			.stream()
			.map(r -> new AdminResponses.PollRow((Long) r[0], (String) r[1], (LocalDate) r[2], (Instant) r[4],
					now.isBefore((Instant) r[4]) ? PollStatus.OPEN : PollStatus.CLOSED, r[5] != null, (Long) r[6],
					(Long) r[7]))
			.toList();
		List<ScheduleResponse> schedules = scheduleRepository.findByOrganizationIdOrderByOpenTimeAscIdAsc(organizationId)
			.stream()
			.map(ScheduleResponse::of)
			.toList();
		return new AdminResponses.OrganizationDetail(row, adminRepository.findMembers(organizationId), polls,
				schedules);
	}

	/** 조직 삭제. 멤버·투표·메뉴·응답·정기 투표는 DB의 ON DELETE CASCADE로 함께 지워진다. */
	@Transactional
	public void deleteOrganization(Long adminId, Long organizationId) {
		Organization organization = organizationRepository.findByIdForUpdate(organizationId)
			.orElseThrow(() -> ApiException.notFound(ORGANIZATION_NOT_FOUND));
		organizationRepository.delete(organization);
		events.publishEvent(new OrganizationDeletedEvent(organizationId));
		log.info("관리자 조직 삭제: adminId={}, organizationId={}", adminId, organizationId);
	}

	/** 멤버 내보내기. 탈퇴와 같은 규칙이고, 마지막 멤버였다면 조직이 삭제된다. 초대 링크로는 다시 들어올 수 있다. */
	@Transactional
	public LeaveResponse removeMember(Long adminId, Long organizationId, Long userId) {
		if (!organizationRepository.existsById(organizationId)) {
			throw ApiException.notFound(ORGANIZATION_NOT_FOUND);
		}
		if (!membershipRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
			throw ApiException.notFound("이 조직의 멤버가 아니에요.");
		}
		boolean organizationDeleted = organizationService.removeMember(organizationId, userId);
		log.info("관리자 멤버 내보내기: adminId={}, organizationId={}, userId={}, 조직 삭제={}", adminId, organizationId,
				userId, organizationDeleted);
		return new LeaveResponse(organizationDeleted);
	}

	// 투표·메뉴·정기 투표

	/** 관리자는 멤버가 아니어도 볼 수 있다. 내 응답 칸은 관리자 기준이라 화면에서 쓰지 않는다. */
	@Transactional(readOnly = true)
	public PollDetailResponse poll(Long adminId, Long pollId) {
		return pollService.detail(findPoll(pollId), adminId);
	}

	/**
	 * 투표 삭제. 메뉴와 응답은 CASCADE로 지워진다.
	 * 정기 투표로 열린 진행 중 투표는 지워도 스케줄러가 곧 다시 열기 때문에, withSchedule이면 규칙도 함께 지운다.
	 */
	@Transactional
	public void deletePoll(Long adminId, Long pollId, boolean withSchedule) {
		Poll poll = findPoll(pollId);
		pollRepository.delete(poll);
		events.publishEvent(new PollDeletedEvent(pollId));
		if (withSchedule && poll.getScheduleId() != null) {
			scheduleRepository.deleteById(poll.getScheduleId());
		}
		log.info("관리자 투표 삭제: adminId={}, pollId={}, 정기 투표 규칙도 삭제={}", adminId, pollId,
				withSchedule && poll.getScheduleId() != null);
	}

	/** 메뉴 강제 삭제: 그 메뉴에 참여한 사람의 응답부터 지운다(그 사람들은 미응답이 된다). */
	@Transactional
	public PollDetailResponse deleteMenuOption(Long adminId, Long optionId) {
		MenuOption option = menuOptionRepository.findById(optionId)
			.orElseThrow(() -> ApiException.notFound("메뉴를 찾을 수 없어요."));
		int deletedVotes = voteRepository.deleteByOptionId(optionId);
		menuOptionRepository.delete(option);
		menuOptionRepository.flush();
		log.info("관리자 메뉴 삭제: adminId={}, pollId={}, optionId={}, 삭제한 응답 수={}", adminId, option.getPollId(),
				optionId, deletedVotes);
		return pollService.detail(findPoll(option.getPollId()), adminId);
	}

	/** 정기 투표 규칙 삭제. 이미 열린 투표는 남는다(polls.schedule_id만 NULL). */
	@Transactional
	public void deleteSchedule(Long adminId, Long scheduleId) {
		PollSchedule schedule = scheduleRepository.findById(scheduleId)
			.orElseThrow(() -> ApiException.notFound("정기 투표 규칙을 찾을 수 없어요."));
		scheduleRepository.delete(schedule);
		log.info("관리자 정기 투표 규칙 삭제: adminId={}, organizationId={}, scheduleId={}", adminId,
				schedule.getOrganizationId(), scheduleId);
	}

	/** 회원 줄에 「제한 중」(지금 진행 중인 제재가 있는지)을 채운다. 제재는 한 쿼리로 읽는다. */
	private List<AdminResponses.UserRow> withRestricted(List<AdminResponses.UserRow> rows) {
		if (rows.isEmpty()) {
			return rows;
		}
		Set<Long> restricted = new HashSet<>(sanctionRepository
			.findRestrictedUserIds(rows.stream().map(AdminResponses.UserRow::id).toList(), Instant.now(clock)));
		return rows.stream().map(row -> row.withRestricted(restricted.contains(row.id()))).toList();
	}

	/** 회원 행을 잠그고 읽는다(같은 회원의 강제 탈퇴·다른 관리자의 처리와 겹치지 않게). 없으면 404 */
	private User lockUser(Long userId) {
		return userRepository.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
	}

	private Poll findPoll(Long pollId) {
		return pollRepository.findById(pollId).orElseThrow(() -> ApiException.notFound(POLL_NOT_FOUND));
	}

	/** 검색어: 앞뒤 공백을 지우고, LIKE 특수문자는 뺀다. 없으면 빈 문자열(전체) */
	private static String keyword(String q) {
		return q == null ? "" : q.strip().replace("%", "").replace("_", "");
	}
}
