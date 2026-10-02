package com.ohjumwhat.menu;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.UserText;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollService;

/**
 * 메뉴 댓글. 멤버 누구나 읽고, 투표가 진행 중일 때만 쓰고 고치고 지운다(마감된 투표는 기록이라 읽기만 한다).
 * 쓰기 API는 그 메뉴의 최신 댓글 목록을 돌려준다.
 */
@Slf4j
@Service
public class MenuCommentService {

	static final int MAX_LENGTH = 200;

	private static final String COMMENT_NOT_FOUND = "댓글을 찾을 수 없어요.";

	private final MenuCommentRepository menuCommentRepository;

	private final MenuOptionRepository menuOptionRepository;

	private final MenuService menuService;

	private final PollService pollService;

	private final Clock clock;

	public MenuCommentService(MenuCommentRepository menuCommentRepository, MenuOptionRepository menuOptionRepository,
			MenuService menuService, PollService pollService, Clock clock) {
		this.menuCommentRepository = menuCommentRepository;
		this.menuOptionRepository = menuOptionRepository;
		this.menuService = menuService;
		this.pollService = pollService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<MenuCommentResponse> list(Long pollId, Long optionId, Long userId) {
		pollService.getForMember(pollId, userId);
		menuService.findInPoll(pollId, optionId);
		return comments(optionId, userId);
	}

	@Transactional
	public List<MenuCommentResponse> add(Long pollId, Long optionId, Long userId, String rawBody) {
		Poll poll = pollService.getForMember(pollId, userId);
		menuService.findInPoll(pollId, optionId);
		pollService.requireOpen(poll);
		String body = UserText.normalize(rawBody, MAX_LENGTH, false);
		MenuComment comment = menuCommentRepository.save(new MenuComment(optionId, userId, body, Instant.now(clock)));
		log.info("댓글 작성: pollId={}, optionId={}, commentId={}, userId={}", pollId, optionId, comment.getId(), userId);
		return comments(optionId, userId);
	}

	/** 내가 쓴 댓글만, 투표가 진행 중일 때 고칠 수 있다. */
	@Transactional
	public List<MenuCommentResponse> edit(Long pollId, Long optionId, Long commentId, Long userId, String rawBody) {
		Poll poll = pollService.getForMember(pollId, userId);
		menuService.findInPoll(pollId, optionId);
		MenuComment comment = findInOption(optionId, commentId);
		if (!userId.equals(comment.getUserId())) {
			throw ApiException.forbidden("내가 쓴 댓글만 고칠 수 있어요.");
		}
		pollService.requireOpen(poll);
		comment.edit(UserText.normalize(rawBody, MAX_LENGTH, false), Instant.now(clock));
		log.info("댓글 수정: pollId={}, optionId={}, commentId={}, userId={}", pollId, optionId, commentId, userId);
		return comments(optionId, userId);
	}

	/** 내가 쓴 댓글만, 투표가 진행 중일 때 지울 수 있다. */
	@Transactional
	public List<MenuCommentResponse> delete(Long pollId, Long optionId, Long commentId, Long userId) {
		Poll poll = pollService.getForMember(pollId, userId);
		menuService.findInPoll(pollId, optionId);
		MenuComment comment = findInOption(optionId, commentId);
		if (!userId.equals(comment.getUserId())) {
			throw ApiException.forbidden("내가 쓴 댓글만 지울 수 있어요.");
		}
		pollService.requireOpen(poll);
		menuCommentRepository.delete(comment);
		log.info("댓글 삭제: pollId={}, optionId={}, commentId={}, userId={}", pollId, optionId, commentId, userId);
		return comments(optionId, userId);
	}

	/** 관리자 콘솔: 메뉴의 댓글(조직·마감과 무관) */
	@Transactional(readOnly = true)
	public List<MenuCommentResponse> listForAdmin(Long optionId, Long adminId) {
		if (!menuOptionRepository.existsById(optionId)) {
			throw ApiException.notFound("메뉴를 찾을 수 없어요.");
		}
		return comments(optionId, adminId);
	}

	/** 관리자 콘솔: 부적절한 댓글 지우기(마감과 무관). 그 메뉴의 남은 댓글을 돌려준다. */
	@Transactional
	public List<MenuCommentResponse> deleteByAdmin(Long commentId, Long adminId) {
		MenuComment comment = menuCommentRepository.findById(commentId)
			.orElseThrow(() -> ApiException.notFound(COMMENT_NOT_FOUND));
		menuCommentRepository.delete(comment);
		log.info("관리자 댓글 삭제: adminId={}, optionId={}, commentId={}", adminId, comment.getOptionId(), commentId);
		return comments(comment.getOptionId(), adminId);
	}

	private MenuComment findInOption(Long optionId, Long commentId) {
		return menuCommentRepository.findById(commentId)
			.filter(c -> c.getOptionId().equals(optionId))
			.orElseThrow(() -> ApiException.notFound(COMMENT_NOT_FOUND));
	}

	private List<MenuCommentResponse> comments(Long optionId, Long userId) {
		return menuCommentRepository.findRows(optionId).stream().map(row -> MenuCommentResponse.of(row, userId)).toList();
	}
}
