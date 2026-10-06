package com.ohjumwhat.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushMessagesTest {

	@Test
	void 이름은_20자까지_그대로_넘으면_20자와_말줄임표다() {
		assertThat(PushMessages.name("가".repeat(20))).isEqualTo("가".repeat(20));
		assertThat(PushMessages.name("가".repeat(21))).isEqualTo("가".repeat(20) + "…");
		assertThat(PushMessages.name("김철수")).isEqualTo("김철수");
	}

	@Test
	void 글자_수는_코드_포인트로_센다() {
		String emoji = "😀"; // UTF-16으로 두 칸
		assertThat(PushMessages.name(emoji.repeat(20))).isEqualTo(emoji.repeat(20));
		assertThat(PushMessages.name(emoji.repeat(21))).isEqualTo(emoji.repeat(20) + "…");
	}

	@Test
	void 방명록_문구() {
		assertThat(PushMessages.guestbook("이영희")).isEqualTo(new PushMessage(PushKind.GUESTBOOK, "이영희님이 방명록을 남겼어요"));
		assertThat(PushMessages.guestbook("가".repeat(25)).title()).isEqualTo("가".repeat(20) + "…님이 방명록을 남겼어요");
		assertThat(PushMessages.guestbookRestricted())
			.isEqualTo(new PushMessage(PushKind.GUESTBOOK_RESTRICTED, "남긴 방명록 글이 관리자에 의해 제한됐어요"));
	}

	@Test
	void 쪽지_문구() {
		assertThat(PushMessages.letter(false, false, "김철수")).contains(new PushMessage(PushKind.LETTER, "김철수님이 쪽지를 보냈어요"));
		assertThat(PushMessages.letter(false, true, "김철수")).contains(new PushMessage(PushKind.LETTER, "김철수님이 답장을 보냈어요"));
		// 익명이면 쪽지·답장 모두 이름 없이
		assertThat(PushMessages.letter(true, false, null)).contains(new PushMessage(PushKind.LETTER, "익명 쪽지가 왔어요"));
		assertThat(PushMessages.letter(true, true, null)).contains(new PushMessage(PushKind.LETTER, "익명 쪽지가 왔어요"));
		// 실명인데 보낸 사람이 없으면(그사이 탈퇴) 보내지 않는다.
		assertThat(PushMessages.letter(false, false, null)).isEmpty();
	}

	@Test
	void data는_종류_제목_주소_묶음_네_개다() {
		assertThat(PushMessages.guestbookRestricted().data()).containsOnlyKeys("kind", "title", "url", "tag")
			.containsEntry("kind", "GUESTBOOK_RESTRICTED")
			.containsEntry("url", "/me")
			.containsEntry("tag", "ohjumwhat-guestbook-restricted");
	}
}
