package com.ohjumwhat.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.UserText;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.poll.PollService;

/**
 * 투표 채팅. 멤버 누구나 읽고, 투표가 열린 때부터 마감 1시간 뒤까지 쓰고 고치고 지운다(내 글만).
 * 쓰고 고치고 지우면 커밋 뒤에 같은 투표의 WebSocket 연결로 보낸다({@link ChatHub}). 로그에는 본문을 남기지 않는다.
 */
@Slf4j
@Service
public class ChatService {

	static final int MAX_LENGTH = 300;

	static final int PAGE_SIZE = 50;

	private static final String MESSAGE_NOT_FOUND = "메시지를 찾을 수 없어요.";

	private final ChatMessageRepository chatMessageRepository;

	private final ChatReadRepository chatReadRepository;

	private final PollService pollService;

	private final PollRepository pollRepository;

	private final ChatRateLimiter rateLimiter;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public ChatService(ChatMessageRepository chatMessageRepository, ChatReadRepository chatReadRepository,
			PollService pollService, PollRepository pollRepository, ChatRateLimiter rateLimiter,
			ApplicationEventPublisher events, Clock clock) {
		this.chatMessageRepository = chatMessageRepository;
		this.chatReadRepository = chatReadRepository;
		this.pollService = pollService;
		this.pollRepository = pollRepository;
		this.rateLimiter = rateLimiter;
		this.events = events;
		this.clock = clock;
	}

	/** before(메시지 ID)보다 오래된 메시지 50개(없으면 최신 50개)를 오래된 → 최신 순으로. 내가 마지막으로 본 위치도 함께 준다. */
	@Transactional(readOnly = true)
	public ChatMessagesResponse list(Long pollId, Long userId, Long before) {
		pollService.getForMember(pollId, userId);
		return page(pollId, before, chatReadRepository.findLastReadId(pollId, userId).orElse(0L));
	}

	/** 보내면 내 메시지까지 읽은 것으로 한다(그 앞의 남의 메시지도 보고 쓴 것이다). */
	@Transactional
	public ChatMessageResponse send(Long pollId, Long userId, String rawBody) {
		Poll poll = pollService.getForMember(pollId, userId);
		requireChatOpen(poll);
		String body = UserText.normalize(rawBody, MAX_LENGTH, true);
		rateLimiter.acquire(userId);
		ChatMessage message = chatMessageRepository.save(new ChatMessage(pollId, userId, body, Instant.now(clock)));
		chatReadRepository.markRead(pollId, userId, message.getId());
		ChatMessageResponse response = response(message.getId());
		events.publishEvent(ChatEvent.created(pollId, response));
		log.info("채팅 보내기: pollId={}, messageId={}, userId={}", pollId, message.getId(), userId);
		return response;
	}

	/** 내가 쓴 메시지만, 채팅이 열려 있을 때 고칠 수 있다. */
	@Transactional
	public ChatMessageResponse edit(Long pollId, Long messageId, Long userId, String rawBody) {
		Poll poll = pollService.getForMember(pollId, userId);
		ChatMessage message = findActive(pollId, messageId);
		if (!userId.equals(message.getUserId())) {
			throw ApiException.forbidden("내가 쓴 메시지만 고칠 수 있어요.");
		}
		requireChatOpen(poll);
		message.edit(UserText.normalize(rawBody, MAX_LENGTH, true), Instant.now(clock));
		chatMessageRepository.flush();
		ChatMessageResponse response = response(messageId);
		events.publishEvent(ChatEvent.updated(pollId, response));
		log.info("채팅 고치기: pollId={}, messageId={}, userId={}", pollId, messageId, userId);
		return response;
	}

	/** 내가 쓴 메시지만, 채팅이 열려 있을 때 지울 수 있다. 행은 남고 「삭제된 메시지예요」로 보인다. */
	@Transactional
	public ChatMessageResponse delete(Long pollId, Long messageId, Long userId) {
		Poll poll = pollService.getForMember(pollId, userId);
		ChatMessage message = findActive(pollId, messageId);
		if (!userId.equals(message.getUserId())) {
			throw ApiException.forbidden("내가 쓴 메시지만 지울 수 있어요.");
		}
		requireChatOpen(poll);
		return softDelete(message, "채팅 지우기: pollId={}, messageId={}, userId={}", userId);
	}

	/**
	 * 「lastReadId까지 봤다」. 위치는 뒤로 가지 않고 그 투표의 마지막 메시지를 넘지 않는다({@link ChatReadRepository#markRead}).
	 * 채팅이 닫힌 뒤에도 된다(지난 투표의 채팅도 읽는다).
	 */
	@Transactional
	public void markRead(Long pollId, Long userId, long lastReadId) {
		pollService.getForMember(pollId, userId);
		if (lastReadId > 0) {
			chatReadRepository.markRead(pollId, userId, lastReadId);
		}
		log.debug("채팅 읽음: pollId={}, userId={}, lastReadId={}", pollId, userId, lastReadId);
	}

	/** 관리자 콘솔: 투표의 채팅(조직·기간과 무관). 관리자에게는 읽은 위치가 없다(0). */
	@Transactional(readOnly = true)
	public ChatMessagesResponse listForAdmin(Long pollId, Long before) {
		if (!pollRepository.existsById(pollId)) {
			throw ApiException.notFound("투표를 찾을 수 없어요.");
		}
		return page(pollId, before, 0L);
	}

	/** 관리자 콘솔: 부적절한 메시지 지우기(채팅이 닫힌 뒤에도) */
	@Transactional
	public ChatMessageResponse deleteByAdmin(Long messageId, Long adminId) {
		ChatMessage message = chatMessageRepository.findById(messageId)
			.filter(m -> !m.isDeleted())
			.orElseThrow(() -> ApiException.notFound(MESSAGE_NOT_FOUND));
		return softDelete(message, "관리자 채팅 지우기: pollId={}, messageId={}, adminId={}", adminId);
	}

	/** 채팅은 투표가 열린 때부터 마감 1시간 뒤까지 쓸 수 있다. */
	private void requireChatOpen(Poll poll) {
		if (!poll.isChatOpen(Instant.now(clock))) {
			throw ApiException.conflict("채팅이 닫혔어요.");
		}
	}

	private ChatMessageResponse softDelete(ChatMessage message, String logFormat, Long actorId) {
		message.delete(Instant.now(clock));
		chatMessageRepository.flush();
		ChatMessageResponse response = response(message.getId());
		events.publishEvent(ChatEvent.deleted(message.getPollId(), response));
		log.info(logFormat, message.getPollId(), message.getId(), actorId);
		return response;
	}

	private ChatMessage findActive(Long pollId, Long messageId) {
		return chatMessageRepository.findById(messageId)
			.filter(m -> m.getPollId().equals(pollId) && !m.isDeleted())
			.orElseThrow(() -> ApiException.notFound(MESSAGE_NOT_FOUND));
	}

	private ChatMessagesResponse page(Long pollId, Long before, long lastReadId) {
		List<ChatMessageRow> rows = chatMessageRepository.findPage(pollId, before == null ? Long.MAX_VALUE : before,
				PageRequest.of(0, PAGE_SIZE + 1));
		boolean hasMore = rows.size() > PAGE_SIZE;
		List<ChatMessageResponse> messages = rows.stream().limit(PAGE_SIZE).map(ChatMessageResponse::of).toList()
			.reversed();
		return new ChatMessagesResponse(messages, hasMore, lastReadId);
	}

	private ChatMessageResponse response(Long messageId) {
		return chatMessageRepository.findRow(messageId).map(ChatMessageResponse::of).orElseThrow();
	}
}
