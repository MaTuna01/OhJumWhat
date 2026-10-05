package com.ohjumwhat.letter;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

/**
 * 쪽지(보는 사람 기준).
 * @param organization 어느 조직에서 보냈는지(조직이 없어지면 null, 「삭제된 조직」)
 * @param counterpart 상대(받은 쪽지는 보낸 사람, 보낸 쪽지는 받는 사람). 숨겼거나(counterpartHidden) 탈퇴했으면 null
 * @param counterpartHidden 상대를 「익명」으로 보여준다(받은 익명 쪽지, 익명 쪽지에 쓴 답장)
 * @param anonymous 받은 쪽지: 익명으로 왔는지, 보낸 쪽지: 내가 익명으로 보냈는지
 * @param readAt 받은 사람이 처음 읽은 시각(안 읽었으면 null)
 * @param replyTo 답장이면 원래 쪽지와 그 첫 줄
 * @param canReply 받은 쪽지에 답장할 수 있는지(조직이 있고, 내가 그 조직 멤버이고, 보낸 사람 계정이 있다. 익명 쪽지는
 *     계정을 보지 않는다)
 * @param replyAnonymous 받은 쪽지에 답장하면 익명으로만 간다(내가 익명으로 보낸 쪽지에 온 답장이라 상대가 나를 모른다)
 * @param reported 받은 쪽지를 내가 신고했는지
 */
public record LetterResponse(Long id, LetterBox box, OrganizationRef organization, PersonResponse counterpart,
		boolean counterpartHidden, boolean anonymous, String body, Instant createdAt, Instant readAt, ReplyRef replyTo,
		boolean canReply, boolean replyAnonymous, boolean reported) {

	public record OrganizationRef(Long id, String name) {
	}

	public record ReplyRef(Long id, String preview) {
	}
}
