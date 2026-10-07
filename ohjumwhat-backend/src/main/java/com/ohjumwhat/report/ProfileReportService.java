package com.ohjumwhat.report;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.UserText;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.sanction.SanctionReason;
import com.ohjumwhat.sanction.UserSanction;

/**
 * 사람 신고(이슈 #115): 같은 조직 멤버를 프로필 모달에서 신고하고(프로필 내용 + 행동), 관리자가 「문제 없음」·제재(경고 포함)·
 * 강제 탈퇴로 처리하면 신고한 사람에게 결과를 알린다(커밋 뒤 푸시 + 다음 화면의 결과 창). 신고한 사실과 신고한 사람은 신고된
 * 사람에게 알리지 않는다. 관리자를 신고해도 받는다(관리자인지 드러내지 않게, 관리자는 「문제 없음」으로만 처리된다).
 * 로그에는 ID만 남기고 설명은 남기지 않는다.
 */
@Slf4j
@Service
public class ProfileReportService {

	static final int DETAIL_MAX_LENGTH = 100;

	static final int RESULT_LIMIT = 20;

	static final int SEEN_LIMIT = 50;

	static final int ADMIN_LIST_LIMIT = 100;

	private final ProfileReportRepository reportRepository;

	private final MembershipRepository membershipRepository;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ProfileReportService(ProfileReportRepository reportRepository, MembershipRepository membershipRepository,
			ApplicationEventPublisher events, Clock clock) {
		this.reportRepository = reportRepository;
		this.membershipRepository = membershipRepository;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 신고한다. 확인 순서: 나 자신(400) → 없는 회원·조직을 같이 쓰지 않음(404) → 사유(400) → 설명(쪽지 신고 사유와 같은 규칙,
	 * 한 줄 100자). 같은 사람에 대한 처리 전 신고가 이미 있으면 아무것도 하지 않는다(처리된 뒤에는 다시 신고할 수 있다).
	 */
	@Transactional
	public void report(Long reporterId, Long targetId, String rawReason, String rawDetail) {
		if (reporterId.equals(targetId)) {
			throw ApiException.badRequest("나 자신은 신고할 수 없어요.");
		}
		// 같은 조직이면 회원이 있다(멤버십은 회원이 지워지면 함께 지워진다).
		if (!membershipRepository.sharesOrganization(reporterId, targetId)) {
			throw ApiException.notFound("사람을 찾을 수 없어요.");
		}
		SanctionReason reason = reason(rawReason);
		String detail = rawDetail == null || rawDetail.isBlank() ? null
				: UserText.normalize(rawDetail, DETAIL_MAX_LENGTH, false);
		int created = reportRepository.insertOpen(reporterId, targetId, reason.name(), detail, Instant.now(clock));
		if (created > 0) {
			log.info("사람 신고: reporterId={}, targetId={}, reason={}", reporterId, targetId, reason);
		}
	}

	/** 아직 결과 창에서 보지 않은 내 신고의 처리 결과(처리 시각 오래된 순 20건) */
	@Transactional(readOnly = true)
	public List<ReportResult> results(Long reporterId) {
		return reportRepository.findUnseenResults(reporterId, PageRequest.of(0, RESULT_LIMIT))
			.stream()
			.map(ReportResult::of)
			.toList();
	}

	/** 결과 창을 봤다. 내가 신고했고 처리된 것만 바꾸고, 남의 ID·처리 전·이미 본 ID는 그대로 둔다. */
	@Transactional
	public void markResultsSeen(Long reporterId, List<Long> ids) {
		if (ids == null || ids.isEmpty() || ids.size() > SEEN_LIMIT || ids.stream().anyMatch(Objects::isNull)) {
			throw ApiException.badRequest("확인한 안내가 올바르지 않아요.");
		}
		int seen = reportRepository.markResultsSeen(reporterId, Set.copyOf(ids), Instant.now(clock));
		log.debug("신고 결과 확인: userId={}, 확인한 수={}", reporterId, seen);
	}

	// 관리자 콘솔

	/** 신고 목록(최신순 100건). openOnly면 처리 전만 */
	@Transactional(readOnly = true)
	public List<ProfileReportRow> list(boolean openOnly) {
		return reportRepository.findRows(openOnly, PageRequest.of(0, ADMIN_LIST_LIMIT))
			.stream()
			.map(ProfileReportRow.Source::toRow)
			.toList();
	}

	/** 문제 없음. 이미 「문제 없음」이면 그대로, 다른 결과로 처리했으면 409. 처리하면 신고한 사람에게 알린다. */
	@Transactional
	public ProfileReportRow dismiss(Long adminId, Long reportId) {
		ProfileReport report = reportRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없어요."));
		if (report.getResolution() == null) {
			report.resolve(ProfileReportResolution.DISMISSED, adminId, Instant.now(clock));
			reportRepository.flush();
			events.publishEvent(new ProfileReportResolvedEvent(reportId, report.getReporterId()));
			log.info("관리자 사람 신고 문제 없음: adminId={}, reportId={}", adminId, reportId);
		}
		else if (report.getResolution() != ProfileReportResolution.DISMISSED) {
			throw ApiException.conflict("이미 처리한 신고예요.");
		}
		return reportRepository.findRow(reportId).orElseThrow().toRow();
	}

	/**
	 * 제재(SanctionService.apply, 같은 트랜잭션, 회원 행을 잠근 뒤): 그 사람에 대한 처리 전 신고를 모두 잠가 「조치함」으로 처리하고,
	 * 제재에 실제로 저장된 제한·초기화·끝나는 시각을 결과 사본으로 둔다. 경고(제한·초기화 없음)여도 「조치함」이다.
	 *
	 * @return 처리한 신고 수
	 */
	@Transactional
	public int resolveOpenAgainst(Long targetId, Long adminId, UserSanction sanction) {
		Instant now = Instant.now(clock);
		List<ProfileReport> reports = reportRepository.findOpenAgainstForUpdate(targetId);
		for (ProfileReport report : reports) {
			report.action(sanction, adminId, now);
		}
		return resolved(reports);
	}

	/**
	 * 강제 탈퇴(AdminService.withdraw, 같은 트랜잭션, 회원 행을 잠근 뒤): 그 사람에 대한 처리 전 신고를 모두 잠가 「탈퇴 처리」로
	 * 처리한다. 회원을 지우면 신고된 사람을 알 수 없으므로 지우기 전에 부른다. 그 사람이 쓴 처리 전 신고는 그대로 둔다.
	 *
	 * @return 처리한 신고 수
	 */
	@Transactional
	public int resolveOpenAgainstWithdrawn(Long targetId, Long adminId) {
		Instant now = Instant.now(clock);
		List<ProfileReport> reports = reportRepository.findOpenAgainstForUpdate(targetId);
		for (ProfileReport report : reports) {
			report.resolve(ProfileReportResolution.WITHDRAWN, adminId, now);
		}
		return resolved(reports);
	}

	@Transactional(readOnly = true)
	public long countOpen() {
		return reportRepository.countOpen();
	}

	@Transactional(readOnly = true)
	public long countOpenAgainst(Long targetId) {
		return reportRepository.countOpenAgainst(targetId);
	}

	/**
	 * 처리한 신고를 바로 DB에 쓰고(뒤에 오는 update 쿼리가 영속성 컨텍스트를 비워도 잃지 않게) 신고마다 신고한 사람에게 알린다
	 * (커밋 뒤 푸시).
	 */
	private int resolved(List<ProfileReport> reports) {
		if (reports.isEmpty()) {
			return 0;
		}
		reportRepository.flush();
		for (ProfileReport report : reports) {
			events.publishEvent(new ProfileReportResolvedEvent(report.getId(), report.getReporterId()));
		}
		return reports.size();
	}

	private static SanctionReason reason(String raw) {
		if (raw == null) {
			throw ApiException.badRequest("무엇이 문제인지 골라 주세요.");
		}
		try {
			return SanctionReason.valueOf(raw);
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("무엇이 문제인지 골라 주세요.");
		}
	}
}
