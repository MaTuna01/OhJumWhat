package com.ohjumwhat.menu;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.organization.MembershipService;
import com.ohjumwhat.place.PlaceLink;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.vote.VoteRepository;

@Slf4j
@Service
public class MenuService {

	private static final int SUGGESTION_LIMIT = 8;

	private final MenuOptionRepository menuOptionRepository;

	private final VoteRepository voteRepository;

	private final PollService pollService;

	private final MembershipService membershipService;

	private final MenuStatsService menuStatsService;

	public MenuService(MenuOptionRepository menuOptionRepository, VoteRepository voteRepository,
			PollService pollService, MembershipService membershipService, MenuStatsService menuStatsService) {
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.pollService = pollService;
		this.membershipService = membershipService;
		this.menuStatsService = menuStatsService;
	}

	/**
	 * 메뉴 추가(식당은 선택). 추가한 사람이 자동으로 참여하지는 않는다.
	 * place는 컨트롤러가 트랜잭션 밖에서 {@link com.ohjumwhat.place.PlaceLinkResolver}로 정리한 값이다(없으면 null).
	 */
	@Transactional
	public PollDetailResponse add(Long pollId, Long userId, String rawName, PlaceLink place) {
		Poll poll = pollService.getForMember(pollId, userId);
		pollService.requireOpen(poll);
		String name = rawName.strip();
		if (menuOptionRepository.existsByPollIdAndName(pollId, name)) {
			throw ApiException.conflict("이미 있는 메뉴예요.");
		}
		MenuOption option = menuOptionRepository.save(new MenuOption(pollId, userId, name, place));
		log.info("메뉴 추가: pollId={}, optionId={}, userId={}, 식당={}", pollId, option.getId(), userId, place != null);
		return pollService.detail(poll, userId);
	}

	/** 식당 달기·고치기·빼기(place가 null이면 뺀다). 추가한 사람만, 투표가 진행 중일 때 할 수 있다. */
	@Transactional
	public PollDetailResponse changePlace(Long pollId, Long optionId, Long userId, PlaceLink place) {
		Poll poll = pollService.getForMember(pollId, userId);
		MenuOption option = findInPoll(pollId, optionId);
		if (!userId.equals(option.getCreatedBy())) {
			throw ApiException.forbidden("메뉴를 추가한 사람만 링크를 고칠 수 있어요.");
		}
		pollService.requireOpen(poll);
		option.changePlace(place);
		log.info("메뉴 식당 변경: pollId={}, optionId={}, userId={}, 식당={}", pollId, optionId, userId, place != null);
		return pollService.detail(poll, userId);
	}

	/** 추가한 사람만, 참여자가 없고 투표가 진행 중일 때 지울 수 있다. */
	@Transactional
	public PollDetailResponse delete(Long pollId, Long optionId, Long userId) {
		Poll poll = pollService.getForMember(pollId, userId);
		MenuOption option = findInPoll(pollId, optionId);
		if (!userId.equals(option.getCreatedBy())) {
			throw ApiException.forbidden("메뉴를 추가한 사람만 삭제할 수 있어요.");
		}
		pollService.requireOpen(poll);
		if (voteRepository.countByOptionId(optionId) > 0) {
			throw ApiException.conflict("참여한 사람이 있는 메뉴는 삭제할 수 없어요.");
		}
		menuOptionRepository.delete(option);
		log.info("메뉴 삭제: pollId={}, optionId={}, userId={}", pollId, optionId, userId);
		return pollService.detail(poll, userId);
	}

	/**
	 * 자동완성: 같은 조직에서 전에 나온 메뉴 이름(최근 순, 최대 8개)과 마지막으로 먹은 날.
	 * 최근 7일 안에 먹은 메뉴는 뒤로 보낸다.
	 */
	@Transactional(readOnly = true)
	public List<MenuSuggestion> suggestions(Long organizationId, Long userId, String query) {
		membershipService.requireMember(organizationId, userId);
		String keyword = query == null ? "" : query.strip().replace("%", "").replace("_", "");
		List<String> names = menuOptionRepository.findRecentNames(organizationId, keyword,
				PageRequest.of(0, SUGGESTION_LIMIT));
		if (names.isEmpty()) {
			return List.of();
		}
		Map<String, LocalDate> lastEaten = menuStatsService.lastEatenByKey(organizationId);
		LocalDate recentFrom = menuStatsService.recentFrom();
		return names.stream()
			.map(name -> new MenuSuggestion(name, lastEaten.get(MenuStatsService.key(name))))
			.sorted(Comparator.comparing((MenuSuggestion s) -> s.lastEatenOn() != null
					&& !s.lastEatenOn().isBefore(recentFrom)))
			.toList();
	}

	private MenuOption findInPoll(Long pollId, Long optionId) {
		return menuOptionRepository.findById(optionId)
			.filter(o -> o.getPollId().equals(pollId))
			.orElseThrow(() -> ApiException.notFound("메뉴를 찾을 수 없어요."));
	}
}
