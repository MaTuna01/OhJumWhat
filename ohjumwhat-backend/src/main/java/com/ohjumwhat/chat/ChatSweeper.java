package com.ohjumwhat.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ohjumwhat.organization.MemberResponse;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;

/**
 * 채팅 연결 점검(30초마다, {@link ChatSweepScheduler}).
 * 채팅이 닫힌 투표(마감 1시간 뒤)와 없어진 투표의 연결을 끊고, 멤버가 아니게 된 사람의 연결을 끊고(이벤트를 놓친 경우의 안전망),
 * 나머지에는 연결 확인 신호({"type":"ping"})를 보낸다. 연결이 있는 투표·조직만 읽는다.
 */
@Component
class ChatSweeper {

	private final ChatHub chatHub;

	private final PollRepository pollRepository;

	private final MembershipRepository membershipRepository;

	private final ChatRateLimiter rateLimiter;

	private final Clock clock;

	ChatSweeper(ChatHub chatHub, PollRepository pollRepository, MembershipRepository membershipRepository,
			ChatRateLimiter rateLimiter, Clock clock) {
		this.chatHub = chatHub;
		this.pollRepository = pollRepository;
		this.membershipRepository = membershipRepository;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	void sweep() {
		Set<Long> pollIds = chatHub.connectedPollIds();
		if (!pollIds.isEmpty()) {
			Instant now = Instant.now(clock);
			Map<Long, Poll> polls = pollRepository.findAllById(pollIds).stream()
				.collect(Collectors.toMap(Poll::getId, Function.identity()));
			for (Long pollId : pollIds) {
				Poll poll = polls.get(pollId);
				if (poll == null) {
					chatHub.closePoll(pollId, ChatHub.NOT_ALLOWED);
				}
				else if (!poll.isChatOpen(now)) {
					chatHub.closePoll(pollId, ChatHub.CHAT_CLOSED);
				}
			}
			chatHub.connectedUsersByOrganization().forEach((organizationId, userIds) -> {
				Set<Long> members = membershipRepository.findMembers(organizationId).stream()
					.map(MemberResponse::userId)
					.collect(Collectors.toSet());
				chatHub.closeNonMembers(organizationId, members);
			});
		}
		chatHub.ping();
		rateLimiter.prune();
	}
}
