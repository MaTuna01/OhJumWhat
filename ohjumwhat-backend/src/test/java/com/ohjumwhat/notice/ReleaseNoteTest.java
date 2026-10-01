package com.ohjumwhat.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ReleaseNoteTest {

	@Test
	void 첫_줄은_제목_나머지는_본문이다() {
		ReleaseNote note = ReleaseNote.parse("1.5.0.md", "﻿# 별명이 생겼어요  \r\n\r\n- 별명을 정할 수 있어요.\r\n- 지난 투표를 볼 수 있어요.\r\n");

		assertThat(note.version()).isEqualTo("1.5.0");
		assertThat(note.title()).isEqualTo("별명이 생겼어요");
		assertThat(note.body()).isEqualTo("- 별명을 정할 수 있어요.\n- 지난 투표를 볼 수 있어요.");
	}

	@Test
	void 형식이_맞지_않으면_이유와_함께_거절한다() {
		assertThatThrownBy(() -> ReleaseNote.parse("v1.5.0.md", "# 제목\n본문"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("v1.5.0.md")
			.hasMessageContaining("파일 이름");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.md", "# 제목\n본문")).hasMessageContaining("파일 이름");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.0.md", "제목\n본문")).hasMessageContaining("첫 줄");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.0.md", "#제목\n본문")).hasMessageContaining("첫 줄");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.0.md", "# \n본문")).hasMessageContaining("제목");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.0.md", "# " + "가".repeat(101) + "\n본문"))
			.hasMessageContaining("제목");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.0.md", "# 제목만 있어요")).hasMessageContaining("본문");
		assertThatThrownBy(() -> ReleaseNote.parse("1.5.0.md", "# 제목\n" + "가".repeat(5001)))
			.hasMessageContaining("본문");
	}

	@Test
	void 버전은_숫자_순서로_정렬한다() {
		List<ReleaseNote> notes = List.of(note("1.10.0"), note("1.9.0"), note("2.0.0"), note("1.9.1"));

		assertThat(notes.stream().sorted(ReleaseNote.BY_VERSION).map(ReleaseNote::version))
			.containsExactly("1.9.0", "1.9.1", "1.10.0", "2.0.0");
	}

	/** 저장소의 실제 업데이트 글이 모두 형식에 맞는지 CI에서 확인한다. 운영에서는 잘못된 파일을 건너뛰기만 해서 놓치기 쉽다. */
	@Test
	void 실제_업데이트_글_파일은_모두_형식에_맞는다() throws Exception {
		Resource[] files = new PathMatchingResourcePatternResolver().getResources(ReleaseNoteSync.LOCATION);

		assertThat(files).isNotEmpty();
		for (Resource file : files) {
			ReleaseNote.parse(file.getFilename(), file.getContentAsString(StandardCharsets.UTF_8));
		}
	}

	private static ReleaseNote note(String version) {
		return new ReleaseNote(version, "제목", "본문");
	}
}
