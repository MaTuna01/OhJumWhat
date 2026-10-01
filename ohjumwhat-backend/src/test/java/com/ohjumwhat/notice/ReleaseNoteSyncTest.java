package com.ohjumwhat.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;

class ReleaseNoteSyncTest extends IntegrationTest {

	@Autowired
	NoticeService noticeService;

	@Autowired
	NoticeRepository noticeRepository;

	Instant deployedAt;

	@BeforeEach
	void setUp() {
		clock.set(2026, 10, 1, 9, 0);
		deployedAt = clock.instant();
	}

	@Test
	void 새_버전은_지금_게시한다() {
		noticeService.syncReleaseNotes(List.of(new ReleaseNote("1.5.0", "별명이 생겼어요", "- 별명")));

		Notice notice = noticeRepository.findByVersion("1.5.0").orElseThrow();
		assertThat(notice.getKind()).isEqualTo(NoticeKind.RELEASE);
		assertThat(notice.getTitle()).isEqualTo("별명이 생겼어요");
		assertThat(notice.getBody()).isEqualTo("- 별명");
		assertThat(notice.getCreatedBy()).isNull();
		assertThat(notice.getPublishedAt()).isEqualTo(deployedAt);
	}

	@Test
	void 같은_파일로_다시_시작해도_그대로다() {
		List<ReleaseNote> files = List.of(new ReleaseNote("1.5.0", "별명이 생겼어요", "- 별명"));
		noticeService.syncReleaseNotes(files);

		clock.set(2026, 10, 2, 9, 0);
		noticeService.syncReleaseNotes(files);

		assertThat(noticeRepository.count()).isEqualTo(1);
		assertThat(noticeRepository.findByVersion("1.5.0").orElseThrow().getPublishedAt()).isEqualTo(deployedAt);
	}

	@Test
	void 내용을_고치면_게시_시각은_그대로_두고_고친다() {
		noticeService.syncReleaseNotes(List.of(new ReleaseNote("1.5.0", "별명이 생겻어요", "- 별명")));

		clock.set(2026, 10, 2, 9, 0);
		noticeService.syncReleaseNotes(List.of(new ReleaseNote("1.5.0", "별명이 생겼어요", "- 별명을 정해요")));

		Notice notice = noticeRepository.findByVersion("1.5.0").orElseThrow();
		assertThat(notice.getTitle()).isEqualTo("별명이 생겼어요");
		assertThat(notice.getBody()).isEqualTo("- 별명을 정해요");
		assertThat(notice.getPublishedAt()).isEqualTo(deployedAt);
	}

	@Test
	void 파일이_없어진_버전도_지우지_않는다() {
		noticeService.syncReleaseNotes(List.of(new ReleaseNote("1.5.0", "별명이 생겼어요", "- 별명")));

		noticeService.syncReleaseNotes(List.of());

		assertThat(noticeRepository.findByVersion("1.5.0")).isPresent();
	}

	@Test
	void 여러_버전이_한꺼번에_새로_들어오면_새_버전이_목록_위에_온다() {
		noticeService.syncReleaseNotes(List.of(new ReleaseNote("1.10.0", "1.10.0", "본문"),
				new ReleaseNote("1.9.0", "1.9.0", "본문")));

		Notice newer = noticeRepository.findByVersion("1.10.0").orElseThrow();
		Notice older = noticeRepository.findByVersion("1.9.0").orElseThrow();
		assertThat(newer.getPublishedAt()).isEqualTo(older.getPublishedAt());
		assertThat(newer.getId()).isGreaterThan(older.getId());
	}

	@Test
	void 형식이_잘못된_파일은_건너뛰고_나머지를_읽는다() throws Exception {
		List<ReleaseNote> notes = ReleaseNoteSync.load("classpath*:release-notes-sample/*.md");

		assertThat(notes).extracting(ReleaseNote::version).containsExactly("1.0.0");
	}

	@Test
	void 업데이트_글_폴더가_없어도_빈_목록이다() throws Exception {
		assertThat(ReleaseNoteSync.load("classpath*:no-such-folder/*.md")).isEmpty();
	}
}
