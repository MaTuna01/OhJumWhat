package com.ohjumwhat.poll;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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
	public PollDetailResponse create(Long organizationId, Long userId, CreatePollRequest request) {
		membershipService.requireMember(organizationId, userId);
		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.now(clock);
		Instant closesAt = ZonedDateTime.of(today, LocalTime.parse(request.closesAt()), TimeConfig.KST).toInstant();
		if (!closesAt.isAfter(now)) {
			throw ApiException.badRequest("마감 시간은 지금보다 뒤여야 해요.");
		}
		Poll poll = pollRepository.save(Poll.manual(organizationId, userId, request.title().strip(), today, now,
				closesAt));
		log.info("투표 생성: pollId={}, organizationId={}, userId={}", poll.getId(), organizationId, userId);
		return detail(poll, userId);
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
			return new PollDetailResponse.Option(option.getId(), option.getName(), creator, voters, mine,
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
