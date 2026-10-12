package com.ohjumwhat.letter;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "쪽지 한 통(보는 사람 기준). 받은 익명 쪽지에는 보낸 사람의 id·이름·사진이 아예 없다")
public record LetterResponse(
		@Schema(description = "쪽지 ID", example = "120") Long id,
		LetterBox box,
		@Schema(description = "어느 조직에서 보냈는지. 조직이 없어졌으면 null(「삭제된 조직」)", nullable = true)
		OrganizationRef organization,
		@Schema(description = "상대(받은 쪽지는 보낸 사람, 보낸 쪽지는 받는 사람). counterpartHidden이면(익명) 항상 null, "
				+ "탈퇴했으면 null(「탈퇴한 사용자」)", nullable = true)
		PersonResponse counterpart,
		@Schema(description = "상대를 「익명」으로 보여줄지: 받은 익명 쪽지, 또는 익명 쪽지에 내가 쓴 답장(보낸 쪽지함)", example = "false")
		boolean counterpartHidden,
		@Schema(description = "받은 쪽지: 익명으로 왔는지, 보낸 쪽지: 내가 익명으로 보냈는지", example = "false") boolean anonymous,
		@Schema(description = "본문(줄바꿈 포함, 500자)", example = "오늘 점심 같이 가요!") String body,
		@Schema(description = "보낸 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt,
		@Schema(description = "받은 사람이 처음 읽은 시각(UTC). 안 읽었으면 null", example = "2026-10-12T02:35:00Z", nullable = true)
		Instant readAt,
		@Schema(description = "답장이면 원래 쪽지와 그 첫 줄, 아니면 null", nullable = true) ReplyRef replyTo,
		@Schema(description = "받은 쪽지에 답장할 수 있는지(조직이 있고, 내가 그 조직 멤버이고, 보낸 사람 계정이 있다. "
				+ "익명 쪽지는 계정을 보지 않아 답장하면 409일 수 있다). 보낸 쪽지는 항상 false", example = "true")
		boolean canReply,
		@Schema(description = "받은 쪽지에 답장하면 익명으로만 가는지(내가 익명으로 시작한 대화에 온 답장이라 상대가 나를 모른다). "
				+ "true면 화면은 익명 체크를 잠가 보여주고, false면 답장은 실명이다. 보낸 쪽지는 항상 false", example = "false")
		boolean replyAnonymous,
		@Schema(description = "받은 쪽지를 내가 신고했는지. 보낸 쪽지는 항상 false", example = "false") boolean reported) {

	@Schema(name = "LetterOrganization", description = "쪽지를 보낸 조직")
	public record OrganizationRef(@Schema(description = "조직 ID", example = "1") Long id,
			@Schema(description = "조직 이름", example = "개발팀") String name) {
	}

	@Schema(name = "LetterReplyTo", description = "답장한 원래 쪽지")
	public record ReplyRef(@Schema(description = "원래 쪽지 ID", example = "118") Long id,
			@Schema(description = "원래 쪽지의 첫 줄(빈 줄이 아닌 첫 줄 40자, 넘으면 …)", example = "오늘 점심 같이 가요!", nullable = true)
			String preview) {
	}
}
