package com.ohjumwhat.poll;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.TimeConfig;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.organization.MemberResponse;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.MembershipService;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.Vote;
import com.ohjumwhat.vote.VoteRepository;

@Slf4j
@Service
public class PollService {

	static final String POLL_NOT_FOUND = "투표를 찾을 수 없어요.";

	private static final int HISTORY_PAGE_SIZE = 10;

	private final PollRepository pollRepository;

	private final MenuOptionRepository menuOptionRepository;

	private final VoteRepository voteRepository;

	private final MembershipRepository membershipRepository;

	private final MembershipService membershipService;

	private final UserRepository userRepository;

	private final Clock clock;

	public PollService(PollRepository pollRepository, MenuOptionRepository menuOptionRepository,
			VoteRepository voteRepository, MembershipRepository membershipRepository,
			MembershipService membershipService, UserRepository userRepository, Clock clock) {
		this.pollRepository = pollRepository;
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.membershipRepository = membershipRepository;
		this.membershipService = membershipService;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/** 수동 투표: 만들면 바로 열리고, 오늘(한국 날짜) 지정한 시각에 마감된다. */
	@Transactional
	public PollDetailResponse create(Long organizationId, Long userId, PollRequest request) {
		membershipService.requireMember(organizationId, userId);
		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.now(clock);
		Instant closesAt = closingTime(today, request.closesAt(), now);
		Poll poll = pollRepository.save(Poll.manual(organizationId, userId, request.title().strip(), today, now,
				closesAt));
		log.info("투표 생성: pollId={}, organizationId={}, userId={}", poll.getId(), organizationId, userId);
		return detail(poll, userId);
	}

	/**
	 * 진행 중인 투표의 제목·마감 시간 수정. 조직 멤버 누구나 할 수 있다(정기 투표도 그 투표만 바뀐다).
	 * 마감 시간은 지금보다 뒤여야 한다. 앞당겨 끝내려면 {@link #close}를 쓴다.
	 */
	@Transactional
	public PollDetailResponse update(Long pollId, Long userId, PollRequest request) {
		Poll poll = getForMember(pollId, userId);
		requireOpen(poll);
		Instant closesAt = closingTime(poll.getPollDate(), request.closesAt(), Instant.now(clock));
		poll.update(request.title().strip(), closesAt);
		log.info("투표 수정: pollId={}, userId={}", pollId, userId);
		return detail(poll, userId);
	}

	/** 조기 마감: 조직 멤버 누구나 진행 중인 투표를 지금 마감할 수 있다. 되돌릴 수 없다. */
	@Transactional
	public PollDetailResponse close(Long pollId, Long userId) {
		Poll poll = getForMember(pollId, userId);
		requireOpen(poll);
		Instant now = Instant.now(clock);
		// polls CHECK(closes_at > opens_at): 열린 직후(1초 안)에는 마감 시각을 오픈 시각 뒤로 둘 수 없다.
		if (now.isBefore(poll.getOpensAt().plusSeconds(1))) {
			throw ApiException.conflict("방금 열린 투표예요. 잠시 후 다시 시도해 주세요.");
		}
		poll.closeAt(now);
		log.info("투표 조기 마감: pollId={}, userId={}", pollId, userId);
		return detail(poll, userId);
	}

	/**
	 * 진행 중인 수동 투표 삭제. 메뉴와 응답은 DB CASCADE로 함께 지워진다.
	 * 정기 투표로 열린 투표는 지워도 스케줄러가 1분 안에 다시 열기 때문에 삭제 대신 조기 마감을 쓴다.
	 * 마감된 투표는 기록으로 남긴다.
	 */
	@Transactional
	public void delete(Long pollId, Long userId) {
		Poll poll = getForMember(pollId, userId);
		requireOpen(poll);
		if (poll.getScheduleId() != null) {
			throw ApiException.conflict("정기 투표는 삭제할 수 없어요. 대신 지금 마감해 주세요.");
		}
		pollRepository.delete(poll);
		log.info("투표 삭제: pollId={}, organizationId={}, userId={}", pollId, poll.getOrganizationId(), userId);
	}

	/** 투표 날짜(한국)의 "HH:mm"을 마감 시각으로 바꾼다. 지금보다 뒤여야 한다. */
	private static Instant closingTime(LocalDate pollDate, String hhmm, Instant now) {
		Instant closesAt = ZonedDateTime.of(pollDate, LocalTime.parse(hhmm), TimeConfig.KST).toInstant();
		if (!closesAt.isAfter(now)) {
			throw ApiException.badRequest("마감 시간은 지금보다 뒤여야 해요.");
		}
		return closesAt;
	}

	@Transactional(readOnly = true)
	public List<PollSummaryResponse> today(Long organizationId, Long userId) {
		membershipService.requireMember(organizationId, userId);
		Instant now = Instant.now(clock);
		int memberCount = (int) membershipRepository.countByOrganizationId(organizationId);
		return pollRepository.findByOrganizationIdAndPollDateOrderByOpensAtAscIdAsc(organizationId, LocalDate.now(clock))
			.stream()
			.map(poll -> summary(poll, userId, memberCount, now))
			.toList();
	}

	/** 지난 투표: 오늘(한국 날짜) 이전 투표를 최신순으로 10개씩. 메뉴·응답은 페이지 단위로 한 번에 읽는다. */
	@Transactional(readOnly = true)
	public PollHistoryResponse history(Long organizationId, Long userId, int page) {
		membershipService.requireMember(organizationId, userId);
		if (page < 0) {
			throw ApiException.badRequest("페이지 번호가 올바르지 않아요.");
		}
		Slice<Poll> polls = pollRepository.findByOrganizationIdAndPollDateBefore(organizationId, LocalDate.now(clock),
				PageRequest.of(page, HISTORY_PAGE_SIZE,
						Sort.by(Sort.Order.desc("pollDate"), Sort.Order.desc("opensAt"), Sort.Order.desc("id"))));
		List<Long> pollIds = polls.map(Poll::getId).toList();
		Map<Long, List<MenuOption>> optionsByPoll = pollIds.isEmpty() ? Map.of()
				: menuOptionRepository.findByPollIdIn(pollIds).stream()
					.collect(Collectors.groupingBy(MenuOption::getPollId));
		Map<Long, List<Vote>> votesByPoll = pollIds.isEmpty() ? Map.of()
				: voteRepository.findByPollIdIn(pollIds).stream().collect(Collectors.groupingBy(Vote::getPollId));
		List<PollHistoryResponse.Item> items = polls.map(poll -> historyItem(poll, userId,
				optionsByPoll.getOrDefault(poll.getId(), List.of()), votesByPoll.getOrDefault(poll.getId(), List.of())))
			.toList();
		return new PollHistoryResponse(items, polls.hasNext());
	}

	@Transactional(readOnly = true)
	public PollDetailResponse get(Long organizationId, Long pollId, Long userId) {
		Poll poll = getForMember(pollId, userId);
		if (!poll.getOrganizationId().equals(organizationId)) {
			throw ApiException.notFound(POLL_NOT_FOUND);
		}
		return detail(poll, userId);
	}

	/** 투표를 찾고, 그 조직의 멤버인지 확인한다. 아니면 투표가 있는지도 알리지 않도록 404로 응답한다. */
	@Transactional(readOnly = true)
	public Poll getForMember(Long pollId, Long userId) {
		Poll poll = pollRepository.findById(pollId).orElseThrow(() -> ApiException.notFound(POLL_NOT_FOUND));
		if (!membershipService.isMember(poll.getOrganizationId(), userId)) {
			throw ApiException.notFound(POLL_NOT_FOUND);
		}
		return poll;
	}

	/** 메뉴 추가·삭제·참여는 진행 중인 투표에서만 할 수 있다. */
	public void requireOpen(Poll poll) {
		if (poll.isClosed(Instant.now(clock))) {
			throw ApiException.conflict("마감된 투표예요.");
		}
	}

	/** 투표 상세 화면 전체: 메뉴별 참여자, 내 응답, 패스, 미응답자, 참여자가 한 명뿐인 메뉴 */
	@Transactional(readOnly = true)
	public PollDetailResponse detail(Poll poll, Long userId) {
		boolean closed = poll.isClosed(Instant.now(clock));
		List<MenuOption> options = menuOptionRepository.findByPollIdOrderByIdAsc(poll.getId());
		List<Vote> votes = voteRepository.findByPollIdOrderByUpdatedAtAscIdAsc(poll.getId());
		List<MemberResponse> members = membershipRepository.findMembers(poll.getOrganizationId());

		// 명단에 필요한 사람: 현재 멤버 + (마감된 투표라면) 이미 탈퇴한 참여자와 메뉴 작성자
		Set<Long> userIds = new HashSet<>();
		votes.forEach(v -> userIds.add(v.getUserId()));
		options.stream().map(MenuOption::getCreatedBy).filter(Objects::nonNull).forEach(userIds::add);
		members.forEach(m -> userIds.remove(m.userId()));
		Map<Long, PersonResponse> people = new LinkedHashMap<>();
		members.forEach(m -> people.put(m.userId(), new PersonResponse(m.userId(), m.name(), m.profileImageUrl())));
		userRepository.findAllById(userIds).forEach(u -> people.put(u.getId(), PersonResponse.of(u)));

		Map<Long, List<PersonResponse>> votersByOption = new LinkedHashMap<>();
		List<PersonResponse> passed = new ArrayList<>();
		for (Vote vote : votes) {
			PersonResponse person = people.get(vote.getUserId());
			if (vote.isPass()) {
				passed.add(person);
			}
			else {
				votersByOption.computeIfAbsent(vote.getOptionId(), id -> new ArrayList<>()).add(person);
			}
		}

		List<PollDetailResponse.Option> optionResponses = options.stream().map(option -> {
			List<PersonResponse> voters = votersByOption.getOrDefault(option.getId(), List.of());
			// 추가한 사람이 강제 탈퇴로 삭제됐으면 createdBy는 null이다("탈퇴한 사용자").
			boolean mine = userId.equals(option.getCreatedBy());
			PersonResponse creator = option.getCreatedBy() == null ? null : people.get(option.getCreatedBy());
			return new PollDetailResponse.Option(option.getId(), option.getName(), option.getLinkUrl(),
					option.getPlaceName(), option.getPlaceAddress(), creator, voters, mine,
					mine && voters.isEmpty() && !closed);
		}).toList();

		Set<Long> responded = votes.stream().map(Vote::getUserId).collect(Collectors.toSet());
		List<PersonResponse> nonRespondents = members.stream()
			.filter(m -> !responded.contains(m.userId()))
			.map(m -> people.get(m.userId()))
			.toList();
		List<Long> soloOptionIds = optionResponses.stream().filter(o -> o.voters().size() == 1)
			.map(PollDetailResponse.Option::id).toList();

		Vote myVote = votes.stream().filter(v -> v.getUserId().equals(userId)).findFirst().orElse(null);
		MyResponse myResponse = myVote == null ? MyResponse.NONE : myVote.isPass() ? MyResponse.PASS : MyResponse.OPTION;

		return new PollDetailResponse(poll.getId(), poll.getOrganizationId(), poll.getTitle(),
				closed ? PollStatus.CLOSED : PollStatus.OPEN, poll.getOpensAt(), poll.getClosesAt(),
				poll.getScheduleId() != null, members.size(), optionResponses, myResponse,
				myVote == null ? null : myVote.getOptionId(), passed, nonRespondents, soloOptionIds);
	}

	private static PollHistoryResponse.Item historyItem(Poll poll, Long userId, List<MenuOption> options,
			List<Vote> votes) {
		Map<Long, Long> countByOption = votes.stream()
			.filter(v -> !v.isPass())
			.collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));
		List<PollHistoryResponse.Team> teams = options.stream()
			.filter(o -> countByOption.containsKey(o.getId()))
			.sorted(Comparator.comparing((MenuOption o) -> countByOption.get(o.getId())).reversed()
				.thenComparing(MenuOption::getId))
			.map(o -> new PollHistoryResponse.Team(o.getName(), countByOption.get(o.getId()).intValue()))
			.toList();
		Vote myVote = votes.stream().filter(v -> v.getUserId().equals(userId)).findFirst().orElse(null);
		MyResponse myResponse = myVote == null ? MyResponse.NONE : myVote.isPass() ? MyResponse.PASS : MyResponse.OPTION;
		String myOptionName = myResponse != MyResponse.OPTION ? null
				: options.stream().filter(o -> o.getId().equals(myVote.getOptionId())).map(MenuOption::getName)
					.findFirst().orElse(null);
		int passCount = (int) votes.stream().filter(Vote::isPass).count();
		return new PollHistoryResponse.Item(poll.getId(), poll.getTitle(), poll.getPollDate(), poll.getClosesAt(),
				votes.size(), passCount, teams, myResponse, myOptionName);
	}

	private PollSummaryResponse summary(Poll poll, Long userId, int memberCount, Instant now) {
		List<Vote> votes = voteRepository.findByPollIdOrderByUpdatedAtAscIdAsc(poll.getId());
		Map<Long, MenuOption> options = menuOptionRepository.findByPollIdOrderByIdAsc(poll.getId()).stream()
			.collect(Collectors.toMap(MenuOption::getId, Function.identity()));
		int passCount = (int) votes.stream().filter(Vote::isPass).count();
		int teamCount = (int) votes.stream().map(Vote::getOptionId).filter(Objects::nonNull).distinct().count();
		Vote myVote = votes.stream().filter(v -> v.getUserId().equals(userId)).findFirst().orElse(null);
		MyResponse myResponse = myVote == null ? MyResponse.NONE : myVote.isPass() ? MyResponse.PASS : MyResponse.OPTION;
		String myOptionName = myResponse == MyResponse.OPTION ? options.get(myVote.getOptionId()).getName() : null;
		return new PollSummaryResponse(poll.getId(), poll.getTitle(),
				poll.isClosed(now) ? PollStatus.CLOSED : PollStatus.OPEN, poll.getClosesAt(), memberCount,
				votes.size(), passCount, options.size(), teamCount, myResponse, myOptionName);
	}
}
