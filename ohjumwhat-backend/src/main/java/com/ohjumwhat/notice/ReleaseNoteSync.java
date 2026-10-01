package com.ohjumwhat.notice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 서버가 뜰 때 저장소의 업데이트 글(src/main/resources/release-notes/{버전}.md)을 새 소식에 게시한다.
 * 배포하면 새 버전 파일이 이미지에 들어가므로, 게시 시각이 곧 배포 시각이 된다.
 * 어떤 오류가 나도 서버 시작은 막지 않는다(시작이 실패하면 배포 헬스 체크가 실패해 서비스가 멈춘다).
 * 테스트에서는 ohjumwhat.release-notes.enabled=false로 끄고 {@link NoticeService#syncReleaseNotes}를 직접 부른다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ohjumwhat.release-notes.enabled", havingValue = "true", matchIfMissing = true)
class ReleaseNoteSync implements ApplicationRunner {

	/** classpath*:는 폴더가 없어도 예외 없이 빈 목록을 준다(classpath:는 예외). */
	static final String LOCATION = "classpath*:release-notes/*.md";

	private final NoticeService noticeService;

	ReleaseNoteSync(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			noticeService.syncReleaseNotes(load(LOCATION));
		}
		catch (Exception e) {
			log.error("업데이트 글을 게시하지 못했습니다. 서버는 계속 실행합니다.", e);
		}
	}

	/** 형식이 잘못된 파일은 경고만 남기고 건너뛴다. */
	static List<ReleaseNote> load(String location) throws IOException {
		List<ReleaseNote> notes = new ArrayList<>();
		for (Resource resource : new PathMatchingResourcePatternResolver().getResources(location)) {
			try {
				notes.add(ReleaseNote.parse(resource.getFilename(),
						resource.getContentAsString(StandardCharsets.UTF_8)));
			}
			catch (IllegalArgumentException e) {
				log.warn("업데이트 글 파일을 건너뜁니다: {}", e.getMessage());
			}
		}
		return notes;
	}
}
