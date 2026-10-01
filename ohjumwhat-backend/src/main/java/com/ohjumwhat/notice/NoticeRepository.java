package com.ohjumwhat.notice;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

	/** 새 소식 목록. 정렬은 Pageable로 준다. */
	Slice<Notice> findAllBy(Pageable pageable);

	/** 안 읽은 공지 수: 기준 시각(마지막으로 본 시각, 없으면 가입 시각)보다 늦게 게시된 공지 */
	long countByPublishedAtAfter(Instant since);

	/** 안 읽은 공지 중 가장 최근 것(배너) */
	Optional<Notice> findFirstByPublishedAtAfterOrderByPublishedAtDescIdDesc(Instant since);

	Optional<Notice> findByVersion(String version);
}
