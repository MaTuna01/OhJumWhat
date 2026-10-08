package com.ohjumwhat.ranking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.TimeConfig;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.menu.MenuStatsService;
import com.ohjumwhat.organization.MemberResponse;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.MembershipService;
import com.ohjumwhat.poll.PersonResponse;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.vote.Vote;
import com.ohjumwhat.vote.VoteRepository;

/**
 * 메뉴 메이커 랭킹: 기간 안에 마감된 투표마다 채택({@link MenuAdoption})을 정하고, 채택된 메뉴를 추가한 사람별로 센다.
 * 별도 테이블 없이 볼 때마다 계산한다. 조직을 떠난 사람은 빼고(기록은 남아 다시 들어오면 보인다),
 * 추가한 사람이 강제 탈퇴한 메뉴는 아무도 받지 않는다.
 */
@Service
public class RankingService {

	private final PollRepository pollRepository;

	private final MenuOptionRepository menuOptionRepository;

	private final VoteRepository voteRepository;

	private final MembershipRepository membershipRepository;

	private final MembershipService membershipService;

	private final Clock clock;

	public RankingService(PollRepository pollRepository, MenuOptionRepository menuOptionRepository,
			VoteRepository voteRepository, MembershipRepository membershipRepository,
			MembershipService membershipService, Clock clock) {
		this.pollRepository = pollRepository;
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.membershipRepository = membershipRepository;
		this.membershipService = membershipService;
		this.clock = clock;
	}

	/** date가 들어 있는 기간의 랭킹. date가 없으면 오늘(한국)이다. */
	@Transactional(readOnly = true)
	public RankingResponse ranking(Long organizationId, Long userId, RankingPeriod period, LocalDate date) {
		membershipService.requireMember(organizationId, userId);
		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.ofInstant(now, TimeConfig.KST);
		LocalDate day = date == null ? today : date;
		if (day.isAfter(today)) {
			throw ApiException.badRequest("아직 오지 않은 기간이에요.");
		}
		RankingPeriod.Range range = period.rangeOf(day);
		if (range.to().isBefore(RankingPeriod.earliest(today))) {
			throw ApiException.badRequest("랭킹은 12개월 전까지 볼 수 있어요.");
		}

		List<Poll> polls = pollRepository.findClosedBetween(organizationId, range.from(), range.to(), now);
		List<Long> pollIds = polls.stream().map(Poll::getId).toList();
		Map<Long, MenuOption> options = pollIds.isEmpty() ? Map.of()
				: menuOptionRepository.findByPollIdIn(pollIds).stream()
					.collect(Collectors.toMap(MenuOption::getId, Function.identity()));
		Map<Long, List<Vote>> votesByPoll = pollIds.isEmpty() ? Map.of()
				: voteRepository.findByPollIdIn(pollIds).stream().collect(Collectors.groupingBy(Vote::getPollId));
		Map<Long, MemberResponse> members = membershipRepository.findMembers(organizationId).stream()
			.collect(Collectors.toMap(MemberResponse::userId, Function.identity()));
		Map<Long, Instant> joinedAt = members.values().stream()
			.collect(Collectors.toMap(MemberResponse::userId, MemberResponse::joinedAt));

		List<Adopted> adopted = new ArrayList<>();
		int adoptedPollCount = 0;
		for (Poll poll : polls) {
			Adoption adoption = MenuAdoption.decide(votesByPoll.getOrDefault(poll.getId(), List.of()), joinedAt,
					poll.getClosesAt());
			if (adoption.optionId() == null) {
				continue;
			}
			adoptedPollCount++;
			MenuOption option = options.get(adoption.optionId());
			if (option.getCreatedBy() != null && members.containsKey(option.getCreatedBy())) {
				adopted.add(new Adopted(option.getCreatedBy(), option.getName(), poll.getPollDate(),
						poll.getClosesAt(), poll.getId()));
			}
		}

		List<RankingResponse.Entry> entries = entries(adopted, members);
		RankingResponse.Me me = entries.stream()
			.filter(e -> e.user().userId().equals(userId))
			.findFirst()
			.map(e -> new RankingResponse.Me(e.rank(), e.count()))
			.orElse(new RankingResponse.Me(null, 0));
		return new RankingResponse(period, range.from(), range.to(), range.hasPrevious(today), range.hasNext(today),
				polls.size(), adoptedPollCount, entries, me);
	}

	/**
	 * 사람별 채택 횟수와 순위. 같은 횟수는 같은 순위이고, 보여주는 순서는 그 횟수에 먼저 도달한 사람
	 * (마지막 채택 투표의 마감이 이른 사람), 그다음 이름 순이다.
	 */
	private static List<RankingResponse.Entry> entries(List<Adopted> adopted, Map<Long, MemberResponse> members) {
		record Tally(MemberResponse member, List<Adopted> adoptions) {

			Instant reachedAt() {
				return adoptions.getFirst().closesAt();
			}
		}
		List<Tally> tallies = adopted.stream()
			.sorted(Adopted.RECENT_FIRST)
			.collect(Collectors.groupingBy(Adopted::userId, LinkedHashMap::new, Collectors.toList()))
			.entrySet().stream()
			.map(e -> new Tally(members.get(e.getKey()), e.getValue()))
			.sorted(Comparator.comparing((Tally t) -> t.adoptions().size()).reversed()
				.thenComparing(Tally::reachedAt)
				.thenComparing(t -> t.member().name()))
			.toList();

		List<RankingResponse.Entry> entries = new ArrayList<>();
		for (int i = 0; i < tallies.size(); i++) {
			Tally tally = tallies.get(i);
			int count = tally.adoptions().size();
			int rank = i > 0 && entries.get(i - 1).count() == count ? entries.get(i - 1).rank() : i + 1;
			MemberResponse member = tally.member();
			List<RankingResponse.Menu> menus = menus(tally.adoptions());
			entries.add(new RankingResponse.Entry(rank,
					new PersonResponse(member.userId(), member.name(), member.profileImageUrl()), count,
					menus.getFirst().name(), menus));
		}
		return entries;
	}

	/** 채택된 메뉴를 이름(통계와 같은 키)별로 묶어 횟수 많은 순, 같으면 최근에 채택된 순. adoptions는 최근 순이다. */
	private static List<RankingResponse.Menu> menus(List<Adopted> adoptions) {
		Map<String, List<Adopted>> byKey = adoptions.stream()
			.collect(Collectors.groupingBy(a -> MenuStatsService.key(a.menuName()), LinkedHashMap::new,
					Collectors.toList()));
		return byKey.values().stream()
			.map(list -> new RankingResponse.Menu(list.getFirst().menuName(), list.size(),
					list.getFirst().pollDate()))
			.sorted(Comparator.comparing(RankingResponse.Menu::count).reversed())
			.toList();
	}

	/** 지금 멤버가 추가한 메뉴가 채택된 투표 하나 */
	private record Adopted(Long userId, String menuName, LocalDate pollDate, Instant closesAt, Long pollId) {

		static final Comparator<Adopted> RECENT_FIRST = Comparator.comparing(Adopted::closesAt)
			.thenComparing(Adopted::pollId)
			.reversed();
	}
}
