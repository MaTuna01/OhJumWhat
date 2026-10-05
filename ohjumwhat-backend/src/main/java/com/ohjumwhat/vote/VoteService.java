package com.ohjumwhat.vote;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollService;

@Slf4j
@Service
public class VoteService {

	private final VoteRepository voteRepository;

	private final MenuOptionRepository menuOptionRepository;

	private final PollService pollService;

	public VoteService(VoteRepository voteRepository, MenuOptionRepository menuOptionRepository,
			PollService pollService) {
		this.voteRepository = voteRepository;
		this.menuOptionRepository = menuOptionRepository;
		this.pollService = pollService;
	}

	/**
	 * 한 메뉴에 참여하거나(optionId), 오늘은 패스한다(null). 마감 전까지 몇 번이든 바꿀 수 있다.
	 */
	@Transactional
	public PollDetailResponse vote(Long pollId, Long userId, Long optionId) {
		Poll poll = pollService.getForMember(pollId, userId);
		pollService.requireOpen(poll);
		if (optionId != null && menuOptionRepository.findById(optionId)
			.filter(o -> o.getPollId().equals(pollId))
			.isEmpty()) {
			throw ApiException.badRequest("이 투표의 메뉴가 아니에요.");
		}
		voteRepository.upsert(pollId, userId, optionId);
		log.info("참여: pollId={}, userId={}, optionId={}", pollId, userId, optionId == null ? "패스" : optionId);
		return pollService.detail(poll, userId);
	}

	/**
	 * 참여·패스를 취소해 미응답으로 돌아간다(응답 행을 지운다). 마감 전에만 되고, 응답이 없어도 그대로 성공한다.
	 */
	@Transactional
	public PollDetailResponse cancel(Long pollId, Long userId) {
		Poll poll = pollService.getForMember(pollId, userId);
		pollService.requireOpen(poll);
		int deleted = voteRepository.deleteByPollIdAndUserId(pollId, userId);
		log.info("응답 취소: pollId={}, userId={}, deleted={}", pollId, userId, deleted);
		return pollService.detail(poll, userId);
	}
}
