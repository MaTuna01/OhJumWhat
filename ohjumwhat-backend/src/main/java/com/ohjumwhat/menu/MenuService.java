package com.ohjumwhat.menu;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.organization.MembershipService;
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

	public MenuService(MenuOptionRepository menuOptionRepository, VoteRepository voteRepository,
			PollService pollService, MembershipService membershipService) {
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.pollService = pollService;
		this.membershipService = membershipService;
	}

	/** 메뉴 추가. 추가한 사람이 자동으로 참여하지는 않는다. */
	@Transactional
	public PollDetailResponse add(Long pollId, Long userId, String rawName) {
		Poll poll = pollService.getForMember(pollId, userId);
		pollService.requireOpen(poll);
		String name = rawName.strip();
		if (menuOptionRepository.existsByPollIdAndName(pollId, name)) {
			throw ApiException.conflict("이미 있는 메뉴예요.");
		}
		MenuOption option = menuOptionRepository.save(new MenuOption(pollId, userId, name));
		log.info("메뉴 추가: pollId={}, optionId={}, userId={}", pollId, option.getId(), userId);
		return pollService.detail(poll, userId);
	}

	/** 추가한 사람만, 참여자가 없고 투표가 진행 중일 때 지울 수 있다. */
	@Transactional
	public PollDetailResponse delete(Long pollId, Long optionId, Long userId) {
		Poll poll = pollService.getForMember(pollId, userId);
		MenuOption option = menuOptionRepository.findById(optionId)
			.filter(o -> o.getPollId().equals(pollId))
			.orElseThrow(() -> ApiException.notFound("메뉴를 찾을 수 없어요."));
		if (!option.getCreatedBy().equals(userId)) {
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

	/** 자동완성: 같은 조직에서 전에 나온 메뉴 이름(최근 순, 최대 8개) */
	@Transactional(readOnly = true)
	public List<String> suggestions(Long organizationId, Long userId, String query) {
		membershipService.requireMember(organizationId, userId);
		String keyword = query == null ? "" : query.strip().replace("%", "").replace("_", "");
		return menuOptionRepository.findRecentNames(organizationId, keyword, PageRequest.of(0, SUGGESTION_LIMIT));
	}
}
