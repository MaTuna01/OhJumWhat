package com.ohjumwhat.notice;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 새 소식(업데이트·개발자 노트).
 * 안 읽은 공지는 회원이 「새 소식」을 마지막으로 본 시각(없으면 가입 시각)보다 늦게 게시된 공지다.
 */
@Slf4j
@Service
public class NoticeService {

	private static final int PAGE_SIZE = 10;

	private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

	private final NoticeRepository noticeRepository;

	private final UserRepository userRepository;

	private final Clock clock;

	public NoticeService(NoticeRepository noticeRepository, UserRepository userRepository, Clock clock) {
		this.noticeRepository = noticeRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/** 새 소식 목록(최신순 10개씩). page는 0부터 */
	@Transactional(readOnly = true)
	public NoticePageResponse list(Long userId, int page) {
		if (page < 0) {
			throw ApiException.badRequest("페이지 번호가 올바르지 않아요.");
		}
		Instant seenSince = seenSince(userId);
		Slice<Notice> notices = noticeRepository.findAllBy(PageRequest.of(page, PAGE_SIZE, LATEST_FIRST));
		return new NoticePageResponse(notices.map(notice -> item(notice, seenSince)).toList(), notices.hasNext());
	}

	/** 안 읽은 공지 수와 가장 최근 것 */
	@Transactional(readOnly = true)
	public UnreadNoticesResponse unread(Long userId) {
		Instant seenSince = seenSince(userId);
		long count = noticeRepository.countByPublishedAtAfter(seenSince);
		UnreadNoticesResponse.Latest latest = count == 0 ? null
				: noticeRepository.findFirstByPublishedAtAfterOrderByPublishedAtDescIdDesc(seenSince)
					.map(notice -> new UnreadNoticesResponse.Latest(notice.getId(), notice.getKind(),
							notice.getVersion(), notice.getTitle()))
					.orElse(null);
		return new UnreadNoticesResponse(count, latest);
	}

	/** 「새 소식」을 봤다(배너 닫기 포함). 지금까지 게시된 공지를 모두 읽은 것으로 한다. */
	@Transactional
	public void markSeen(Long userId) {
		if (userRepository.markNoticesSeen(userId, Instant.now(clock)) == 0) {
			throw ApiException.unauthorized("다시 로그인해 주세요.");
		}
	}

	/** 읽음 기준 시각. 강제 탈퇴 등으로 회원이 없어졌다면 401로 응답해 로그인 화면으로 보낸다. */
	private Instant seenSince(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> ApiException.unauthorized("다시 로그인해 주세요."));
		return user.getNoticesSeenAt() != null ? user.getNoticesSeenAt() : user.getCreatedAt();
	}

	private static NoticePageResponse.Item item(Notice notice, Instant seenSince) {
		return new NoticePageResponse.Item(notice.getId(), notice.getKind(), notice.getVersion(), notice.getTitle(),
				notice.getBody(), notice.getPublishedAt(), notice.getPublishedAt().isAfter(seenSince));
	}
}
