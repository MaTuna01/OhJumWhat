package com.ohjumwhat.letter;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.UserText;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.MembershipService;
import com.ohjumwhat.poll.PersonResponse;

/**
 * 쪽지(Notion 「21. 같은 조직에 추가된 사용자들끼리 쪽지 주고받기 기능」). 같은 조직 멤버끼리 한 통씩 주고받고, 익명으로도 보낸다.
 *
 * <p>익명 보호 규칙
 * <ul>
 * <li>받은 익명 쪽지의 응답에는 보낸 사람의 id·이름·사진이 없다(LetterRepository가 고르지 않는다).
 * <li>익명 쪽지에 답장하면 그 답장의 받는 사람을 답장한 사람에게 숨기고(recipientHidden), 익명으로 보낸 사람이 다시 답하면
 * 익명이 강제된다.
 * <li>차단은 익명 여부로 나눈다. 받은 쪽지함에서는 같은 범위의 쪽지만 숨기고, 새로 오는 익명 쪽지는 어느 범위로든 차단돼 있으면
 * 받지 않는다(보낸 사람에게는 보낸 것으로 보인다).
 * <li>답장할 수 없는 이유는 한 문구로 알린다(상대가 조직을 떠났는지는 확인하지 않는다).
 * </ul>
 * 로그에는 본문을 남기지 않는다.
 */
@Slf4j
@Service
public class LetterService {

	static final int MAX_LENGTH = 500;

	static final int REASON_MAX_LENGTH = 100;

	static final int PAGE_SIZE = 20;

	static final int ADMIN_REPORT_LIMIT = 100;

	static final String LETTER_NOT_FOUND = "쪽지를 찾을 수 없어요.";

	static final String CANNOT_REPLY = "답장할 수 없는 쪽지예요.";

	private final LetterRepository letterRepository;

	private final LetterBlockRepository blockRepository;

	private final LetterReportRepository reportRepository;

	private final MembershipService membershipService;

	private final MembershipRepository membershipRepository;

	private final LetterRateLimiter rateLimiter;

	private final Clock clock;

	public LetterService(LetterRepository letterRepository, LetterBlockRepository blockRepository,
			LetterReportRepository reportRepository, MembershipService membershipService,
			MembershipRepository membershipRepository, LetterRateLimiter rateLimiter, Clock clock) {
		this.letterRepository = letterRepository;
		this.blockRepository = blockRepository;
		this.reportRepository = reportRepository;
		this.membershipService = membershipService;
		this.membershipRepository = membershipRepository;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	/** 쪽지함 한 쪽(before보다 오래된 20통, 최신순) */
	@Transactional(readOnly = true)
	public LetterPageResponse list(Long userId, LetterBox box, Long before) {
		long beforeId = before == null ? Long.MAX_VALUE : before;
		PageRequest page = PageRequest.of(0, PAGE_SIZE + 1);
		List<LetterRow> rows = box == LetterBox.RECEIVED ? letterRepository.findReceived(userId, beforeId, page)
				: letterRepository.findSent(userId, beforeId, page);
		boolean hasMore = rows.size() > PAGE_SIZE;
		List<LetterRow> shown = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
		return new LetterPageResponse(responses(userId, box, shown), hasMore);
	}

	@Transactional(readOnly = true)
	public long unreadCount(Long userId) {
		return letterRepository.countUnread(userId);
	}

	/** 같은 조직 멤버에게 보낸다. 보낸 쪽지(보낸 쪽지함 기준)를 돌려준다. */
	@Transactional
	public LetterResponse send(Long userId, Long organizationId, Long recipientId, String rawBody, boolean anonymous) {
		String body = UserText.normalize(rawBody, MAX_LENGTH, true);
		if (userId.equals(recipientId)) {
			throw ApiException.badRequest("나에게는 쪽지를 보낼 수 없어요.");
		}
		membershipService.requireMember(organizationId, userId);
		if (recipientId == null || !membershipService.isMember(organizationId, recipientId)) {
			throw ApiException.notFound("받는 사람을 찾을 수 없어요.");
		}
		rateLimiter.acquire(userId);
		Letter letter = deliver(new Letter(organizationId, userId, recipientId, null, anonymous, false, body,
				Instant.now(clock)));
		log.info("쪽지 보내기: letterId={}, organizationId={}, senderId={}, recipientId={}, anonymous={}", letter.getId(),
				organizationId, userId, recipientId, anonymous);
		return sentResponse(userId, letter.getId());
	}

	/**
	 * 받은 쪽지에 답장한다(원래 보낸 사람에게). 익명 쪽지에 쓴 답장은 받는 사람을 숨기고, 내가 익명으로 보낸 쪽지에 온 답장에
	 * 다시 답하면 익명으로 보낸다.
	 */
	@Transactional
	public LetterResponse reply(Long userId, Long letterId, String rawBody, boolean anonymous) {
		String body = UserText.normalize(rawBody, MAX_LENGTH, true);
		Letter original = letterRepository.findVisibleReceived(letterId, userId)
			.orElseThrow(() -> ApiException.notFound(LETTER_NOT_FOUND));
		if (!canReply(original.getOrganizationId(), original.getSenderId() != null,
				Set.copyOf(membershipRepository.findOrganizationIdsByUserId(userId)))) {
			throw ApiException.conflict(CANNOT_REPLY);
		}
		rateLimiter.acquire(userId);
		Letter letter = deliver(new Letter(original.getOrganizationId(), userId, original.getSenderId(),
				original.getId(), anonymous || original.isRecipientHidden(), original.isAnonymous(), body,
				Instant.now(clock)));
		log.info("쪽지 답장: letterId={}, replyToId={}, senderId={}", letter.getId(), original.getId(), userId);
		return sentResponse(userId, letter.getId());
	}

	/** 받은 쪽지를 처음 열면 읽은 시각을 남긴다. */
	@Transactional
	public void markRead(Long userId, Long letterId) {
		letterRepository.findVisibleReceived(letterId, userId)
			.orElseThrow(() -> ApiException.notFound(LETTER_NOT_FOUND))
			.markRead(Instant.now(clock));
	}

	/** 내 쪽지함에서만 지운다(받은 쪽지·보낸 쪽지 모두). 상대 쪽지함에는 남는다. */
	@Transactional
	public void delete(Long userId, Long letterId) {
		Instant now = Instant.now(clock);
		var sent = letterRepository.findVisibleSent(letterId, userId);
		if (sent.isPresent()) {
			sent.get().deleteForSender(now);
		}
		else {
			letterRepository.findVisibleReceived(letterId, userId)
				.orElseThrow(() -> ApiException.notFound(LETTER_NOT_FOUND))
				.deleteForRecipient(now);
		}
		log.info("쪽지 지우기: letterId={}, userId={}", letterId, userId);
	}

	/** 받은 쪽지의 보낸 사람을 차단한다(그 쪽지의 익명 여부 범위로). 이미 차단했으면 그대로 둔다. */
	@Transactional
	public void block(Long userId, Long letterId) {
		Letter letter = letterRepository.findReceivedIncludingBlocked(letterId, userId)
			.orElseThrow(() -> ApiException.notFound(LETTER_NOT_FOUND));
		block(userId, letter);
	}

	@Transactional(readOnly = true)
	public List<LetterBlockResponse> blocks(Long userId) {
		return blockRepository.findRows(userId)
			.stream()
			.map(row -> new LetterBlockResponse(row.id(), row.anonymous(),
					row.userId() == null ? null : new PersonResponse(row.userId(), row.name(), row.photoUrl()),
					LetterPreview.of(row.letterBody()), row.createdAt()))
			.toList();
	}

	/** 차단을 풀면 차단하기 전에 받은 쪽지가 다시 보인다(차단 중에 받지 않은 쪽지는 계속 숨긴다). */
	@Transactional
	public void unblock(Long userId, Long blockId) {
		LetterBlock block = blockRepository.findByIdAndUserId(blockId, userId)
			.orElseThrow(() -> ApiException.notFound("차단을 찾을 수 없어요."));
		blockRepository.delete(block);
		log.info("쪽지 차단 풀기: blockId={}, userId={}", blockId, userId);
	}

	/** 받은 쪽지를 신고한다(한 쪽지는 한 번만, 다시 신고해도 그대로). block이면 보낸 사람도 차단한다. */
	@Transactional
	public void report(Long userId, Long letterId, String rawReason, boolean block) {
		String reason = rawReason == null || rawReason.isBlank() ? null
				: UserText.normalize(rawReason, REASON_MAX_LENGTH, false);
		// 신고하며 차단해 쪽지가 목록에서 사라진 뒤에 다시 눌러도 그대로이게, 차단으로 숨긴 쪽지도 찾는다.
		Letter letter = letterRepository.findReceivedIncludingBlocked(letterId, userId)
			.orElseThrow(() -> ApiException.notFound(LETTER_NOT_FOUND));
		if (!reportRepository.existsByLetterId(letterId)) {
			LetterReport report = reportRepository.save(new LetterReport(letterId, reason, Instant.now(clock)));
			log.info("쪽지 신고: reportId={}, letterId={}, userId={}", report.getId(), letterId, userId);
		}
		if (block) {
			block(userId, letter);
		}
	}

	// 관리자 콘솔

	/** 신고 목록(최근 100건). openOnly면 처리 전만 */
	@Transactional(readOnly = true)
	public List<LetterReportResponse> reportsForAdmin(boolean openOnly) {
		return reportRepository.findForAdmin(openOnly, PageRequest.of(0, ADMIN_REPORT_LIMIT));
	}

	/** 처리 완료(이미 처리했으면 그대로) */
	@Transactional
	public void resolveReport(Long adminId, Long reportId) {
		reportRepository.findById(reportId)
			.orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없어요."))
			.resolve(adminId, Instant.now(clock));
		log.info("관리자 쪽지 신고 처리: adminId={}, reportId={}", adminId, reportId);
	}

	private void block(Long userId, Letter letter) {
		if (letter.getSenderId() == null) {
			// 보낸 사람이 탈퇴해서 더 받을 쪽지가 없다.
			return;
		}
		if (!blockRepository.existsByUserIdAndBlockedUserIdAndAnonymous(userId, letter.getSenderId(),
				letter.isAnonymous())) {
			LetterBlock block = blockRepository.save(new LetterBlock(userId, letter.getSenderId(), letter.getId(),
					letter.isAnonymous(), Instant.now(clock)));
			log.info("쪽지 차단: blockId={}, userId={}, letterId={}", block.getId(), userId, letter.getId());
		}
	}

	/**
	 * 저장한다. 받는 사람이 보낸 사람을 차단해 두었으면 받는 사람 쪽에서는 지운 상태로 둔다(보낸 사람에게는 보낸 것으로 보인다).
	 * 익명 쪽지는 어느 범위로든 차단돼 있으면 받지 않는다(실명 차단을 익명으로 우회하지 못하게). 실명 쪽지는 실명 차단만 본다
	 * (익명 차단 때문에 실명 쪽지가 안 가면 그 사람이 익명으로 보냈다는 것이 드러난다).
	 */
	private Letter deliver(Letter letter) {
		boolean blocked = letter.isAnonymous()
				? blockRepository.existsByUserIdAndBlockedUserId(letter.getRecipientId(), letter.getSenderId())
				: blockRepository.existsByUserIdAndBlockedUserIdAndAnonymous(letter.getRecipientId(),
						letter.getSenderId(), false);
		if (blocked) {
			letter.dropForRecipient(Instant.now(clock));
		}
		return letterRepository.save(letter);
	}

	private LetterResponse sentResponse(Long userId, Long letterId) {
		LetterRow row = letterRepository.findSentRow(letterId).orElseThrow();
		return responses(userId, LetterBox.SENT, List.of(row)).get(0);
	}

	/** 답장할 수 있는 받은 쪽지: 조직이 있고, 내가 지금 그 조직 멤버이고, 보낸 사람 계정이 있다. */
	private static boolean canReply(Long organizationId, boolean senderPresent, Set<Long> myOrganizationIds) {
		return organizationId != null && senderPresent && myOrganizationIds.contains(organizationId);
	}

	private List<LetterResponse> responses(Long userId, LetterBox box, List<LetterRow> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		boolean received = box == LetterBox.RECEIVED;
		Set<Long> myOrganizationIds = received ? Set.copyOf(membershipRepository.findOrganizationIdsByUserId(userId))
				: Set.of();
		Set<Long> reported = received
				? new HashSet<>(reportRepository.findReportedLetterIds(rows.stream().map(LetterRow::id).toList()))
				: Set.of();
		Map<Long, String> replyBodies = new HashMap<>();
		List<Long> replyIds = rows.stream().map(LetterRow::replyToId).filter(Objects::nonNull).distinct().toList();
		if (!replyIds.isEmpty()) {
			for (Object[] idAndBody : letterRepository.findBodies(replyIds)) {
				replyBodies.put((Long) idAndBody[0], (String) idAndBody[1]);
			}
		}
		return rows.stream().map(row -> {
			boolean hidden = received ? row.anonymous() : row.recipientHidden();
			PersonResponse counterpart = row.counterpartId() == null ? null
					: new PersonResponse(row.counterpartId(), row.counterpartName(), row.counterpartPhotoUrl());
			LetterResponse.OrganizationRef organization = row.organizationId() == null ? null
					: new LetterResponse.OrganizationRef(row.organizationId(), row.organizationName());
			LetterResponse.ReplyRef replyTo = row.replyToId() == null ? null
					: new LetterResponse.ReplyRef(row.replyToId(), LetterPreview.of(replyBodies.get(row.replyToId())));
			return new LetterResponse(row.id(), box, organization, counterpart, hidden, row.anonymous(), row.body(),
					row.createdAt(), row.readAt(), replyTo,
					received && canReply(row.organizationId(), row.senderPresent(), myOrganizationIds),
					received && row.recipientHidden(), reported.contains(row.id()));
		}).toList();
	}
}
