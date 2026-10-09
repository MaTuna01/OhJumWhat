package com.ohjumwhat.loadtest;

import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.loadtest.LoadTestSeeder.CleanResult;
import com.ohjumwhat.loadtest.LoadTestSeeder.SeedSpec;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationRepository;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class LoadTestSeederTest extends IntegrationTest {

	@Autowired
	LoadTestSeeder seeder;

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationRepository organizationRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	ApplicationContext applicationContext;

	Instant closesAt;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		closesAt = Instant.now(clock).plus(Duration.ofHours(3));
	}

	@Test
	void 시드는_조직마다_회원_투표_메뉴_참여와_로그인_세션을_만든다() {
		SeedResult result = seeder.seed(new SeedSpec(2, 3, closesAt, 0.5));

		assertThat(result.orgs()).hasSize(2);
		assertThat(result.memberCount()).isEqualTo(6);
		assertThat(result.closesAt()).isEqualTo(closesAt);
		assertThat(count("users")).isEqualTo(6);
		assertThat(count("organizations")).isEqualTo(2);
		assertThat(count("memberships")).isEqualTo(6);
		assertThat(count("polls")).isEqualTo(2);
		assertThat(count("menu_options")).isEqualTo(8);
		// 3명 × 0.5 = 1.5 → 반올림 2명씩 미리 참여
		assertThat(count("votes")).isEqualTo(4);
		assertThat(count("spring_session")).isEqualTo(6);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from spring_session where principal_name like 'loadtest-%'", Long.class)).isEqualTo(6);
		assertThat(jdbcTemplate.queryForList("select poll_date from polls", String.class))
			.containsOnly("2026-09-30");

		SeedResult.Org org = result.orgs().getFirst();
		assertThat(org.name()).isEqualTo("[부하테스트] 조직 1");
		assertThat(org.optionIds()).hasSize(4);
		assertThat(org.members()).extracting(SeedResult.Member::googleSub)
			.containsExactly("loadtest-1-1", "loadtest-1-2", "loadtest-1-3");
		assertThat(seeder.exists()).isTrue();
	}

	@Test
	void 시드한_세션_쿠키만으로_API를_쓸_수_있다() throws Exception {
		SeedResult result = seeder.seed(new SeedSpec(1, 2, closesAt, 0));
		SeedResult.Org org = result.orgs().getFirst();
		SeedResult.Member member = org.members().getFirst();
		Cookie session = new Cookie("SESSION", member.sessionCookie());

		mockMvc.perform(get("/api/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(member.userId()))
			.andExpect(jsonPath("$.name").value("부하테스트 1-1"));

		mockMvc.perform(get("/api/orgs/" + org.orgId() + "/polls/" + org.pollId()).cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.options", hasSize(4)))
			.andExpect(jsonPath("$.nonRespondents", hasSize(2)));

		// 쓰기: TestAuth.xsrf(sessionId)가 SESSION·XSRF-TOKEN 쿠키와 헤더를 함께 만든다
		String sessionId = new String(Base64.getDecoder().decode(member.sessionCookie()), StandardCharsets.UTF_8);
		mockMvc.perform(put("/api/polls/" + org.pollId() + "/vote").with(xsrf(sessionId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"optionId\": " + org.optionIds().getFirst() + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myOptionId").value(org.optionIds().getFirst()));
	}

	@Test
	void history만큼_지난_투표를_만들어_통계_자동완성에_기록이_생긴다() throws Exception {
		SeedResult result = seeder.seed(new SeedSpec(1, 3, closesAt, 0, 5));
		SeedResult.Org org = result.orgs().getFirst();
		Cookie session = new Cookie("SESSION", org.members().getFirst().sessionCookie());

		// 오늘 투표 1개 + 지난 투표 5개(어제부터 하루에 하나, 11:00~12:00 KST), 메뉴 4개씩, 멤버 전원 참여
		assertThat(count("polls")).isEqualTo(6);
		assertThat(count("menu_options")).isEqualTo(24);
		assertThat(count("votes")).isEqualTo(15);
		assertThat(jdbcTemplate.queryForList("select poll_date::text from polls order by poll_date", String.class))
			.containsExactly("2026-09-25", "2026-09-26", "2026-09-27", "2026-09-28", "2026-09-29", "2026-09-30");
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from polls where poll_date < '2026-09-30' and closes_at <> (poll_date::text || ' 12:00+09')::timestamptz",
				Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("""
				select count(distinct v.option_id) from votes v join polls p on p.id = v.poll_id
				where p.poll_date < '2026-09-30'""", Long.class)).isGreaterThan(5);

		mockMvc.perform(get("/api/orgs/" + org.orgId() + "/polls/history?page=0").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.polls", hasSize(5)))
			.andExpect(jsonPath("$.polls[0].pollDate").value("2026-09-29"));
		mockMvc.perform(get("/api/orgs/" + org.orgId() + "/menu-names?q=찌개").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", hasItem("김치찌개")));
		mockMvc.perform(get("/api/orgs/" + org.orgId() + "/menu-stats").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void 시드_데이터가_남아_있으면_다시_시드하지_않는다() {
		seeder.seed(new SeedSpec(1, 1, closesAt, 0));

		assertThatThrownBy(() -> seeder.seed(new SeedSpec(1, 1, closesAt, 0)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("clean");
		assertThat(count("users")).isEqualTo(1);
	}

	@Test
	void 마감_시각이_지금보다_뒤가_아니면_거절한다() {
		assertThatThrownBy(() -> seeder.seed(new SeedSpec(1, 1, Instant.now(clock), 0)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(count("users")).isZero();
	}

	@Test
	void 정리는_시드_데이터만_지우고_세션도_끝낸다() throws Exception {
		User kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		Long realOrgId = organizationService.create(kim.getId(), "개발팀").id();
		SeedResult result = seeder.seed(new SeedSpec(2, 3, closesAt, 0.5));
		Cookie session = new Cookie("SESSION", result.orgs().getFirst().members().getFirst().sessionCookie());

		CleanResult cleaned = seeder.clean();

		assertThat(cleaned.users()).isEqualTo(6);
		assertThat(cleaned.organizations()).isEqualTo(2);
		assertThat(cleaned.sessions()).isEqualTo(6);
		assertThat(cleaned.skippedOrgIds()).isEmpty();
		assertThat(seeder.exists()).isFalse();
		assertThat(count("users")).isEqualTo(1);
		assertThat(count("organizations")).isEqualTo(1);
		assertThat(count("polls")).isZero();
		assertThat(count("votes")).isZero();
		assertThat(count("spring_session")).isZero();
		assertThat(organizationRepository.findById(realOrgId)).isPresent();
		mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void 실제_회원이_들어온_시드_조직은_지우지_않고_알린다() {
		SeedResult result = seeder.seed(new SeedSpec(2, 2, closesAt, 0));
		User kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		Long mixedOrgId = result.orgs().getFirst().orgId();
		String inviteToken = organizationRepository.findById(mixedOrgId).orElseThrow().getInviteToken();
		inviteService.join(inviteToken, kim.getId());

		CleanResult cleaned = seeder.clean();

		assertThat(cleaned.skippedOrgIds()).containsExactly(mixedOrgId);
		assertThat(cleaned.organizations()).isEqualTo(1);
		assertThat(cleaned.users()).isEqualTo(4);
		// 섞인 조직은 남고, 그 조직의 테스트 회원만 빠져 실제 회원 혼자 남는다
		assertThat(organizationRepository.findById(mixedOrgId)).isPresent();
		assertThat(jdbcTemplate.queryForObject("select count(*) from memberships where organization_id = ?",
				Long.class, mixedOrgId)).isEqualTo(1);
		assertThat(seeder.exists()).isTrue();
	}

	@Test
	void 정리할_것이_없어도_그대로_끝난다() {
		CleanResult cleaned = seeder.clean();

		assertThat(cleaned).isEqualTo(new CleanResult(0, 0, 0, 0, List.of()));
	}

	@Test
	void 시드_러너는_loadtest_프로필_없이는_뜨지_않는다() {
		assertThat(applicationContext.getBeansOfType(LoadTestRunner.class)).isEmpty();
	}

	private long count(String table) {
		Long count = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
		return count == null ? 0 : count;
	}
}
