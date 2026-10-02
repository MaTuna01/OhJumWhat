package com.ohjumwhat.menu;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.organization.MembershipService;

/**
 * 메뉴 통계와 추천. 별도 테이블 없이 지난 투표 기록으로 계산한다.
 * "먹은 메뉴"는 마감된 투표에서 참여자가 한 명 이상인 메뉴다(조직 기준).
 * 메뉴 이름은 대소문자와 띄어쓰기를 무시하고 묶는다("김치찌개" = "김치 찌개").
 */
@Service
public class MenuStatsService {

	/** 최근 7일(오늘 포함) 안에 먹은 메뉴는 추천에서 빼고, 자동완성에서는 뒤로 보낸다. */
	static final int RECENT_DAYS = 7;

	private static final int STATS_LIMIT = 30;

	private static final int RECOMMENDATION_LIMIT = 10;

	private final JdbcClient jdbcClient;

	private final MembershipService membershipService;

	private final MenuOptionRepository menuOptionRepository;

	private final Clock clock;

	public MenuStatsService(JdbcClient jdbcClient, MembershipService membershipService,
			MenuOptionRepository menuOptionRepository, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.membershipService = membershipService;
		this.menuOptionRepository = menuOptionRepository;
		this.clock = clock;
	}

	/** 조직의 먹은 메뉴 순위. days가 있으면 최근 days일(오늘 포함), 없으면 전체 기간 */
	@Transactional(readOnly = true)
	public MenuStatsResponse stats(Long organizationId, Long userId, Integer days) {
		membershipService.requireMember(organizationId, userId);
		if (days != null && (days < 1 || days > 366)) {
			throw ApiException.badRequest("기간은 1일에서 366일 사이로 골라 주세요.");
		}
		LocalDate from = days == null ? null : LocalDate.now(clock).minusDays(days - 1L);
		List<MenuStatsResponse.MenuStat> menus = eaten(organizationId, from).stream()
			.limit(STATS_LIMIT)
			.map(EatenMenu::toStat)
			.toList();
		return new MenuStatsResponse(days, from, closedPollCount(organizationId, from), menus);
	}

	/** 오늘은 이거 어때요? 자주 먹었지만 최근 7일 안에는 먹지 않은 메뉴(많이 먹은 순)와 지난번 붙인 식당 */
	@Transactional(readOnly = true)
	public List<MenuStatsResponse.Recommendation> recommendations(Long organizationId, Long userId) {
		membershipService.requireMember(organizationId, userId);
		LocalDate recentFrom = recentFrom();
		List<EatenMenu> menus = eaten(organizationId, null).stream()
			.filter(menu -> menu.lastEatenOn().isBefore(recentFrom))
			.limit(RECOMMENDATION_LIMIT)
			.toList();
		Map<String, LastPlace> places = lastPlaces(organizationId, menus.stream().map(EatenMenu::name).toList());
		return menus.stream()
			.map(m -> new MenuStatsResponse.Recommendation(m.name(), m.times(), m.people(), m.lastEatenOn(),
					places.get(m.name())))
			.toList();
	}

	/** 메뉴 이름 → 같은 이름의 메뉴에 지난번 붙인 식당(식당이 붙은 가장 최근 메뉴) */
	Map<String, LastPlace> lastPlaces(Long organizationId, Collection<String> names) {
		if (names.isEmpty()) {
			return Map.of();
		}
		Map<String, LastPlace> places = new HashMap<>();
		menuOptionRepository.findWithPlaceByNames(organizationId, names)
			.forEach(option -> places.putIfAbsent(option.getName(), LastPlace.of(option)));
		return places;
	}

	/** 메뉴 이름 키 → 마지막으로 먹은 날(전체 기간). 자동완성 표시용 */
	Map<String, LocalDate> lastEatenByKey(Long organizationId) {
		return eaten(organizationId, null).stream()
			.collect(Collectors.toMap(EatenMenu::menuKey, EatenMenu::lastEatenOn));
	}

	/** 이 날짜 이후(포함)에 먹었으면 "최근에 먹은 메뉴"다. */
	LocalDate recentFrom() {
		return LocalDate.now(clock).minusDays(RECENT_DAYS - 1L);
	}

	/** 메뉴 이름을 묶는 키: 소문자, 띄어쓰기 제거. SQL의 replace(lower(name), ' ', '')와 같다. */
	static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replace(" ", "");
	}

	/**
	 * 마감된 투표에서 참여자가 있었던 메뉴를 이름 키로 묶는다.
	 * times = 먹은 투표 수, people = 참여 인원 합계, name = 가장 최근에 쓴 이름.
	 */
	private List<EatenMenu> eaten(Long organizationId, LocalDate from) {
		String sql = """
				with eaten as (
				    select p.id as poll_id, p.poll_date, m.name, replace(lower(m.name), ' ', '') as menu_key,
				           count(*) as people
				    from polls p
				    join menu_options m on m.poll_id = p.id
				    join votes v on v.option_id = m.id
				    where p.organization_id = :organizationId and p.closes_at <= :now %s
				    group by p.id, p.poll_date, m.id, m.name
				)
				select menu_key, (array_agg(name order by poll_date desc, poll_id desc))[1] as name,
				       count(distinct poll_id) as times, sum(people) as people, max(poll_date) as last_eaten_on
				from eaten
				group by menu_key
				order by times desc, people desc, last_eaten_on desc, name
				""".formatted(from == null ? "" : "and p.poll_date >= :from");
		JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
			.param("organizationId", organizationId)
			.param("now", now());
		if (from != null) {
			statement = statement.param("from", from);
		}
		return statement.query((rs, rowNum) -> new EatenMenu(rs.getString("menu_key"), rs.getString("name"),
				rs.getInt("times"), rs.getInt("people"), rs.getObject("last_eaten_on", LocalDate.class)))
			.list();
	}

	private int closedPollCount(Long organizationId, LocalDate from) {
		String sql = "select count(*) from polls where organization_id = :organizationId and closes_at <= :now"
				+ (from == null ? "" : " and poll_date >= :from");
		JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
			.param("organizationId", organizationId)
			.param("now", now());
		if (from != null) {
			statement = statement.param("from", from);
		}
		return statement.query(Integer.class).single();
	}

	/** PostgreSQL JDBC는 Instant를 바로 받지 못해 timestamptz에 맞는 OffsetDateTime으로 넘긴다. */
	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
	}

	private record EatenMenu(String menuKey, String name, int times, int people, LocalDate lastEatenOn) {

		MenuStatsResponse.MenuStat toStat() {
			return new MenuStatsResponse.MenuStat(name, times, people, lastEatenOn);
		}
	}
}
