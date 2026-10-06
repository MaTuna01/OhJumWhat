package com.ohjumwhat.guestbook;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.UserText;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.poll.PersonResponse;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 방명록(이슈 #91). 나와 조직을 하나라도 같이 쓰는 사람의 프로필에 한 줄(100자) 글을 남긴다. 조직이 아니라 사람에게 속한다.
 *
 * <ul>
 * <li>볼 수 있는 사람: 주인 본인, 그리고 주인과 조직을 하나라도 같이 쓰는 사람. 그 밖에는 방명록이 있는지도 알리지 않는다(404).
 * <li>쓸 수 있는 사람: 주인과 같은 조직의 다른 사람(주인은 자기 방명록에 쓸 수 없다). 한 사람이 모든 방명록을 합쳐 5초에 한 번.
 * <li>지우기는 쓴 사람(지금 같은 조직이 아니어도 된다)과 주인. 관리자가 제한한 글은 주인만 지운다. 행은 남기는 소프트 삭제다(신고가 글을 잃지 않게).
 * <li>신고는 주인만 하고, 관리자가 「글 제한」(본문을 누구에게도 내보내지 않고 쓴 사람에게 경고)·「문제 없음」으로 처리한다.
 * </ul>
 * 로그에는 ID만 남기고 본문은 남기지 않는다.
 */
@Slf4j
@Service
public class GuestbookService {

	static final int MAX_LENGTH = 100;

	static final int REASON_MAX_LENGTH = 100;

	static final int PAGE_SIZE = 10;

	static final int ADMIN_REPORT_LIMIT = 100;

	/** 쪽 번호 상한(offset이 int를 넘어 500이 되지 않게). 한 사람에게 10만 개가 넘는 글은 없다. */
	static final int MAX_PAGE = 10_000;

	static final String GUESTBOOK_NOT_FOUND = "방명록을 찾을 수 없어요.";

	static final String ENTRY_NOT_FOUND = "방명록 글을 찾을 수 없어요.";

	static final String ALREADY_RESOLVED = "이미 처리한 신고예요.";

	private final GuestbookEntryRepository entryRepository;

	private final GuestbookReportRepository reportRepository;

	private final MembershipRepository membershipRepository;

	private final UserRepository userRepository;

	private final GuestbookRateLimiter rateLimiter;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public GuestbookService(GuestbookEntryRepository entryRepository, GuestbookReportRepository reportRepository,
			MembershipRepository membershipRepository, UserRepository userRepository, GuestbookRateLimiter rateLimiter,
			ApplicationEventPublisher events, Clock clock) {
		this.entryRepository = entryRepository;
		this.reportRepository = reportRepository;
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.rateLimiter = rateLimiter;
		this.events = events;
		this.clock = clock;
	}

	/** 방명록 한 쪽(최신순 10개). page는 0부터. 주인이면 내 방명록을 마지막으로 본 시각도 준다. */
	@Transactional(readOnly = true)
	public GuestbookPageResponse list(Long userId, Long ownerId, int page) {
		if (page < 0 || page > MAX_PAGE) {
			throw ApiException.badRequest("페이지 번호가 올바르지 않아요.");
		}
		if (userId.equals(ownerId)) {
			return page(userId, ownerId, page, findMe(userId).getGuestbookSeenAt());
		}
		// 같은 조직이면 주인이 있다(멤버십은 회원이 지워지면 함께 지워진다).
		if (!membershipRepository.sharesOrganization(userId, ownerId)) {
			throw ApiException.notFound(GUESTBOOK_NOT_FOUND);
		}
		return page(userId, ownerId, page, null);
	}

	/**
	 * 남의 방명록에 쓴다. 도배 방지는 글을 확인한 뒤에 센다(잘못 쓴 글로 5초를 쓰지 않게). 쓴 사람이 보는 0쪽을 돌려준다.
	 */
	@Transactional
	public GuestbookPageResponse write(Long userId, Long ownerId, String rawBody) {
		String body = UserText.normalize(rawBody, MAX_LENGTH, false);
		if (userId.equals(ownerId)) {
			throw ApiException.badRequest("내 방명록에는 글을 남길 수 없어요.");
		}
		if (!membershipRepository.sharesOrganization(userId, ownerId)) {
			throw ApiException.notFound(GUESTBOOK_NOT_FOUND);
		}
		rateLimiter.acquire(userId);
		GuestbookEntry entry = entryRepository.save(new GuestbookEntry(ownerId, userId, body, Instant.now(clock)));
		events.publishEvent(new GuestbookEntryCreatedEvent(entry.getId(), ownerId, userId));
		log.info("방명록 쓰기: entryId={}, ownerId={}, authorId={}", entry.getId(), ownerId, userId);
		return page(userId, ownerId, 0, null);
	}

	/**
	 * 쓴 사람이나 주인이 지운다(소프트 삭제). 쓴 사람은 지금 주인과 같은 조직이 아니어도 지울 수 있다. 관리자가 제한한 글은
	 * 주인만 지운다(제한된 줄을 내 방명록에서 치울 수 있게, 쓴 사람이 제재받은 글을 스스로 치우지는 못하게).
	 */
	@Transactional
	public void delete(Long userId, Long entryId) {
		GuestbookEntry entry = entryRepository.findByIdAndDeletedAtIsNull(entryId)
			.orElseThrow(() -> ApiException.notFound(ENTRY_NOT_FOUND));
		if (!userId.equals(entry.getAuthorId()) && !userId.equals(entry.getOwnerId())) {
			throw canView(userId, entry.getOwnerId())
					? ApiException.forbidden("내가 쓴 글이나 내 방명록의 글만 지울 수 있어요.")
					: ApiException.notFound(ENTRY_NOT_FOUND);
		}
		if (entry.getRestrictedAt() != null && !userId.equals(entry.getOwnerId())) {
			throw ApiException.forbidden("관리자가 제한한 글은 방명록 주인만 지울 수 있어요.");
		}
		entry.delete(Instant.now(clock));
		log.info("방명록 지우기: entryId={}, userId={}", entryId, userId);
	}

	/**
	 * 주인이 내 방명록의 글을 신고한다. 이미 신고했거나 제한된 글이면 그대로 둔다. 같은 글의 신고가 동시에 들어오면 UNIQUE에
	 * 걸려 409다.
	 */
	@Transactional
	public void report(Long userId, Long entryId, String rawReason) {
		String reason = rawReason == null || rawReason.isBlank() ? null
				: UserText.normalize(rawReason, REASON_MAX_LENGTH, false);
		GuestbookEntry entry = entryRepository.findByIdAndDeletedAtIsNull(entryId)
			.orElseThrow(() -> ApiException.notFound(ENTRY_NOT_FOUND));
		if (!userId.equals(entry.getOwnerId())) {
			throw canView(userId, entry.getOwnerId()) ? ApiException.forbidden("내 방명록의 글만 신고할 수 있어요.")
					: ApiException.notFound(ENTRY_NOT_FOUND);
		}
		if (entry.getRestrictedAt() != null || reportRepository.existsByEntryId(entryId)) {
			return;
		}
		GuestbookReport report = reportRepository.save(new GuestbookReport(entryId, reason, Instant.now(clock)));
		log.info("방명록 신고: reportId={}, entryId={}, userId={}", report.getId(), entryId, userId);
	}

	/** 알림 요약: 내 방명록의 새 글 수와, 아직 확인하지 않은 제한된 내 글(경고). 회원이 없으면 401 */
	@Transactional(readOnly = true)
	public GuestbookAlertsResponse alerts(Long userId) {
		User me = findMe(userId);
		return new GuestbookAlertsResponse(entryRepository.countNewSince(userId, orEpoch(me.getGuestbookSeenAt())),
				entryRepository.findWarnings(userId, orEpoch(me.getGuestbookWarningsSeenAt())));
	}

	/** 내 방명록을 until(화면에 보인 가장 최근 글의 시각)까지 봤다. 지금보다 뒤면 지금으로 자르고, 뒤로 가지 않는다. */
	@Transactional
	public void markSeen(Long userId, Instant until) {
		findMe(userId);
		userRepository.markGuestbookSeen(userId, notAfterNow(until));
	}

	/** 경고를 until(보여준 경고 중 가장 최근 제한 시각)까지 확인했다. markSeen과 같은 규칙이다. */
	@Transactional
	public void ackWarnings(Long userId, Instant until) {
		findMe(userId);
		userRepository.ackGuestbookWarnings(userId, notAfterNow(until));
	}

	// 관리자 콘솔

	/** 신고 목록(최근 100건). openOnly면 처리 전만 */
	@Transactional(readOnly = true)
	public List<GuestbookReportResponse> reportsForAdmin(boolean openOnly) {
		return reportRepository.findForAdmin(openOnly, PageRequest.of(0, ADMIN_REPORT_LIMIT));
	}

	/**
	 * 글 제한: 지운 글도 제한하고(지워서 제재를 피하지 못하게), 쓴 사람이 있으면 경고 이벤트를 낸다.
	 * 이미 「글 제한」으로 처리했으면 그대로, 「문제 없음」으로 처리했으면 409
	 */
	@Transactional
	public void restrictReport(Long adminId, Long reportId) {
		GuestbookReport report = requireReport(reportId);
		if (!resolve(report, GuestbookReportResolution.RESTRICTED, adminId)) {
			return;
		}
		GuestbookEntry entry = entryRepository.findById(report.getEntryId()).orElseThrow();
		entry.restrict(Instant.now(clock));
		if (entry.getAuthorId() != null) {
			events.publishEvent(new GuestbookEntryRestrictedEvent(entry.getId(), entry.getAuthorId(),
					entry.getOwnerId()));
		}
		log.info("관리자 방명록 글 제한: adminId={}, reportId={}, entryId={}", adminId, reportId, entry.getId());
	}

	/** 문제 없음: 신고만 처리하고 글은 그대로 둔다. 이미 「문제 없음」이면 그대로, 「글 제한」이면 409 */
	@Transactional
	public void dismissReport(Long adminId, Long reportId) {
		GuestbookReport report = requireReport(reportId);
		if (resolve(report, GuestbookReportResolution.DISMISSED, adminId)) {
			log.info("관리자 방명록 신고 문제 없음: adminId={}, reportId={}", adminId, reportId);
		}
	}

	/**
	 * 강제 탈퇴(AdminService.withdraw, 같은 트랜잭션): 그 사람이 쓴 글의 처리 전 신고를 모두 「글 제한」으로 처리하고 그 글들을
	 * 제한한다. 회원을 지우면 쓴 사람을 알 수 없으므로 지우기 전에 부른다. 쓴 사람이 사라지므로 경고 이벤트는 내지 않는다.
	 *
	 * @return 처리한 신고 수
	 */
	@Transactional
	public int restrictOpenReportsAgainst(Long authorId, Long adminId) {
		Instant now = Instant.now(clock);
		// 신고를 처리하면 어느 글의 신고가 열려 있었는지 알 수 없으므로 글부터 제한한다.
		reportRepository.restrictOpenlyReportedEntriesOf(authorId, now);
		return reportRepository.resolveOpenAgainstAuthor(authorId, GuestbookReportResolution.RESTRICTED, adminId, now);
	}

	private GuestbookReport requireReport(Long reportId) {
		return reportRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없어요."));
	}

	/** 처리한다. 같은 결과로 이미 처리했으면 false(그대로), 다른 결과로 처리했으면 409 */
	private boolean resolve(GuestbookReport report, GuestbookReportResolution resolution, Long adminId) {
		if (report.getResolution() != null) {
			if (report.getResolution() != resolution) {
				throw ApiException.conflict(ALREADY_RESOLVED);
			}
			return false;
		}
		report.resolve(resolution, adminId, Instant.now(clock));
		return true;
	}

	/** 볼 수 있는 방명록: 주인이 있고, 내가 주인이거나 주인과 조직을 하나라도 같이 쓴다. */
	private boolean canView(Long userId, Long ownerId) {
		return ownerId != null && (userId.equals(ownerId) || membershipRepository.sharesOrganization(userId, ownerId));
	}

	/** 강제 탈퇴 등으로 회원이 없어졌다면 401로 응답해 로그인 화면으로 보낸다. */
	private User findMe(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("다시 로그인해 주세요."));
	}

	private Instant notAfterNow(Instant until) {
		Instant now = Instant.now(clock);
		return until.isAfter(now) ? now : until;
	}

	/** 한 번도 보지 않았으면 모든 것이 새것이다. */
	private static Instant orEpoch(Instant seenAt) {
		return seenAt == null ? Instant.EPOCH : seenAt;
	}

	/** @param seenAt 주인일 때만 내 방명록을 본 시각(주인이 아니면 null) */
	private GuestbookPageResponse page(Long userId, Long ownerId, int page, Instant seenAt) {
		boolean owner = userId.equals(ownerId);
		Page<GuestbookEntryRow> rows = entryRepository.findPage(ownerId, PageRequest.of(page, PAGE_SIZE));
		Set<Long> reported = owner && rows.hasContent()
				? new HashSet<>(reportRepository.findReportedEntryIds(rows.map(GuestbookEntryRow::id).toList()))
				: Set.of();
		List<GuestbookEntryResponse> entries = rows.map(row -> {
			boolean mine = userId.equals(row.authorId());
			boolean isReported = owner && reported.contains(row.id());
			PersonResponse author = row.authorId() == null ? null
					: new PersonResponse(row.authorId(), row.authorName(), row.authorPhotoUrl());
			// 제한된 글은 주인만 지운다(delete와 같은 규칙).
			return new GuestbookEntryResponse(row.id(), author, row.body(), row.createdAt(), row.restricted(), mine,
					owner || (mine && !row.restricted()), owner && !isReported && !row.restricted(), isReported);
		}).toList();
		return new GuestbookPageResponse(entries, page, rows.getTotalPages(), rows.getTotalElements(), owner,
				owner ? seenAt : null);
	}
}
