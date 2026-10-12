package com.ohjumwhat.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.ohjumwhat.poll.PollStatus;
import com.ohjumwhat.schedule.ScheduleResponse;
import com.ohjumwhat.user.ProfileDetailsResponse;
import com.ohjumwhat.user.Role;
import com.ohjumwhat.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 콘솔 응답. 목록 행은 AdminRepository의 JPQL에서 바로 만든다. */
final class AdminResponses {

	private AdminResponses() {
	}

	/**
	 * 개요: 전체 회원·조직 수, 오늘 투표 수, 지금 진행 중인 투표 수, 최근 7일 가입자 수, 차단 수, 처리할 신고 수,
	 * 지금 이용이 제한된 회원 수(진행 중인 제재가 있는 회원).
	 * openReportCount는 쪽지·방명록·사람(프로필) 신고의 처리 전 신고를 합친 수다
	 * (openLetterReportCount + openGuestbookReportCount + openProfileReportCount).
	 */
	@Schema(name = "AdminStats", description = "관리자 개요(DA01)의 숫자 카드")
	record Stats(
			@Schema(description = "전체 회원 수", example = "120") long userCount,
			@Schema(description = "전체 조직 수", example = "15") long organizationCount,
			@Schema(description = "오늘(한국 날짜) 열린 투표 수", example = "8") long todayPollCount,
			@Schema(description = "지금 진행 중인 투표 수", example = "3") long openPollCount,
			@Schema(description = "최근 7일 가입자 수", example = "5") long newUserCount,
			@Schema(description = "차단한 구글 계정 수(「차단」 탭)", example = "1") long blockedCount,
			@Schema(description = "처리할 신고 수(쪽지 + 방명록 + 프로필의 처리 전 신고 합)", example = "4") long openReportCount,
			@Schema(description = "처리 전 쪽지 신고 수", example = "2") long openLetterReportCount,
			@Schema(description = "처리 전 방명록 신고 수", example = "1") long openGuestbookReportCount,
			@Schema(description = "처리 전 사람(프로필) 신고 수", example = "1") long openProfileReportCount,
			@Schema(description = "지금 이용이 제한된 회원 수(진행 중인 제재가 있는 회원, 「제한 중」 카드)", example = "2")
			long restrictedUserCount) {
	}

	/**
	 * name은 화면 이름(별명, 없으면 구글 이름), googleName은 구글 계정 이름.
	 * profileImageUrl은 화면 사진(올린 사진, 없으면 구글 사진), customPhoto는 올린 사진이 있는지.
	 * restricted는 지금 진행 중인 제재가 있는지(「제한 중」 배지, AdminService가 한 번에 조회해 채운다)
	 */
	@Schema(name = "AdminUserRow", description = "회원 목록(DA02)의 한 줄")
	record UserRow(
			@Schema(description = "회원 ID", example = "7") Long id,
			@Schema(description = "화면 이름(별명, 없으면 구글 이름)", example = "점심요정") String name,
			@Schema(description = "구글 계정 이름", example = "김오점") String googleName,
			@Schema(description = "구글 계정 이메일", example = "kim@example.com") String email,
			@Schema(description = "화면 사진 주소(올린 사진, 없으면 구글 사진). 둘 다 없으면 null",
					example = "/api/photos/3f2a9c1e.jpg", nullable = true) String profileImageUrl,
			@Schema(description = "올린 사진이 있는지(「올린 사진 지우기」를 보일지)", example = "true") boolean customPhoto,
			@Schema(description = "역할(USER 일반, ADMIN 관리자)", example = "USER") Role role,
			@Schema(description = "가입 시각(UTC)", example = "2026-09-01T03:00:00Z") Instant createdAt,
			@Schema(description = "최근 로그인 시각(UTC). 기록이 없으면 null", example = "2026-10-12T02:30:00Z", nullable = true)
			Instant lastLoginAt,
			@Schema(description = "소속 조직 수", example = "2") long organizationCount,
			@Schema(description = "지금 진행 중인 제재가 있는지(「제한 중」 배지)", example = "false") boolean restricted) {

		/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정한다. restricted는 서비스가 withRestricted로 채운다. */
		UserRow(Long id, String name, String googleName, String email, String photoKey, String googleUrl, Role role,
				Instant createdAt, Instant lastLoginAt, long organizationCount) {
			this(id, name, googleName, email, User.photoUrl(photoKey, googleUrl), photoKey != null, role, createdAt,
					lastLoginAt, organizationCount, false);
		}

		UserRow withRestricted(boolean restricted) {
			return new UserRow(id, name, googleName, email, profileImageUrl, customPhoto, role, createdAt, lastLoginAt,
					organizationCount, restricted);
		}
	}

	/**
	 * @param nickname 별명(없으면 null, 화면 이름은 구글 이름). 관리자 「프로필 수정」이 지금 값과 비교한다.
	 * @param bio 한줄 소개(없으면 null)
	 * @param foodTags 좋아하는 음식(없으면 빈 목록)
	 * @param details 상세 프로필(채우지 않았으면 null)
	 * @param lastAccessAt 세션의 마지막 요청 시각(로그인은 30일 유지되므로 최근 로그인보다 최근 활동에 가깝다)
	 * @param openProfileReportCount 이 회원에 대한 처리 전 사람 신고 수(제재하면 함께 「조치함」으로 처리된다)
	 */
	@Schema(name = "AdminUserDetail", description = "회원 상세(DA03). 프로필 수정 API도 모두 이 모양으로 바뀐 상세를 준다")
	record UserDetail(
			@Schema(description = "회원 줄(목록과 같은 모양)") UserRow user,
			@Schema(description = "별명. 없으면 null(화면 이름은 구글 이름). 「프로필 수정」(A03-M3)이 지금 값과 비교한다",
					example = "점심요정", nullable = true) String nickname,
			@Schema(description = "한줄 소개(없으면 null)", example = "국물 요리면 다 좋아요", nullable = true) String bio,
			@Schema(description = "좋아하는 음식(없으면 빈 목록)", example = "[\"김치찌개\", \"떡볶이\"]") List<String> foodTags,
			@Schema(description = "상세 프로필(채우지 않았으면 null)", nullable = true) ProfileDetailsResponse details,
			@Schema(description = "로그인 세션의 마지막 요청 시각(UTC, 최근 활동). 로그인 세션이 없으면 null",
					example = "2026-10-12T02:30:00Z", nullable = true) Instant lastAccessAt,
			@Schema(description = "소속 조직(들어온 순)") List<UserOrganization> organizations,
			@Schema(description = "활동 수") Activity activity,
			@Schema(description = "이 회원에 대한 처리 전 사람 신고 수(제재하면 함께 처리된다)", example = "0")
			long openProfileReportCount) {
	}

	@Schema(name = "AdminUserOrganization", description = "회원 상세의 소속 조직 한 줄")
	record UserOrganization(
			@Schema(description = "조직 ID", example = "1") Long id,
			@Schema(description = "조직 이름", example = "개발팀") String name,
			@Schema(description = "조직 인원", example = "10") long memberCount,
			@Schema(description = "들어온 시각(UTC)", example = "2026-09-01T03:00:00Z") Instant joinedAt,
			@Schema(description = "그 조직을 마지막으로 연 시각(UTC). 기록이 없으면 null", example = "2026-10-12T02:30:00Z",
					nullable = true) Instant lastVisitedAt) {
	}

	/** 그 회원이 만든 투표, 올린 메뉴, 남긴 응답(패스 포함) 수 */
	@Schema(name = "AdminUserActivity", description = "회원의 활동 수")
	record Activity(
			@Schema(description = "만든 투표 수", example = "4") long pollsCreated,
			@Schema(description = "올린 메뉴 수", example = "12") long menusAdded,
			@Schema(description = "남긴 응답 수(패스 포함)", example = "30") long responses) {
	}

	@Schema(name = "AdminOrganizationRow", description = "조직 목록(DA04)의 한 줄")
	record OrganizationRow(
			@Schema(description = "조직 ID", example = "1") Long id,
			@Schema(description = "조직 이름", example = "개발팀") String name,
			@Schema(description = "만든 시각(UTC)", example = "2026-09-01T03:00:00Z") Instant createdAt,
			@Schema(description = "인원", example = "10") long memberCount,
			@Schema(description = "지금까지 연 투표 수", example = "25") long pollCount,
			@Schema(description = "가장 최근 투표 날짜(한국 날짜). 투표가 없으면 null", example = "2026-10-12", nullable = true)
			LocalDate lastPollDate) {
	}

	@Schema(name = "AdminOrganizationDetail", description = "조직 상세(DA05)")
	record OrganizationDetail(
			@Schema(description = "조직 줄(목록과 같은 모양)") OrganizationRow organization,
			@Schema(description = "멤버(들어온 순, 맨 위가 대개 만든 사람)") List<Member> members,
			@Schema(description = "최근 투표 30개(투표 날짜 최신순)") List<PollRow> polls,
			@Schema(description = "정기 투표 규칙(오픈 시각 순)") List<ScheduleResponse> schedules) {
	}

	@Schema(name = "AdminOrganizationMember", description = "조직 상세의 멤버 한 줄")
	record Member(
			@Schema(description = "회원 ID", example = "7") Long userId,
			@Schema(description = "화면 이름(별명, 없으면 구글 이름)", example = "점심요정") String name,
			@Schema(description = "구글 계정 이메일", example = "kim@example.com") String email,
			@Schema(description = "화면 사진 주소(올린 사진, 없으면 구글 사진). 둘 다 없으면 null",
					example = "/api/photos/3f2a9c1e.jpg", nullable = true) String profileImageUrl,
			@Schema(description = "역할(USER 일반, ADMIN 관리자)", example = "USER") Role role,
			@Schema(description = "들어온 시각(UTC)", example = "2026-09-01T03:00:00Z") Instant joinedAt,
			@Schema(description = "그 조직을 마지막으로 연 시각(UTC). 기록이 없으면 null", example = "2026-10-12T02:30:00Z",
					nullable = true) Instant lastVisitedAt) {

		/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정한다. */
		Member(Long userId, String name, String email, String photoKey, String googleUrl, Role role, Instant joinedAt,
				Instant lastVisitedAt) {
			this(userId, name, email, User.photoUrl(photoKey, googleUrl), role, joinedAt, lastVisitedAt);
		}
	}

	/** 조직의 최근 투표. status는 서비스에서 지금 시각으로 정한다. */
	@Schema(name = "AdminPollRow", description = "조직 상세의 최근 투표 한 줄")
	record PollRow(
			@Schema(description = "투표 ID", example = "10") Long id,
			@Schema(description = "제목", example = "오늘 점심") String title,
			@Schema(description = "투표 날짜(한국 날짜)", example = "2026-10-12") LocalDate pollDate,
			@Schema(description = "마감 시각(UTC)", example = "2026-10-12T02:50:00Z") Instant closesAt,
			PollStatus status,
			@Schema(description = "정기 투표로 열린 투표인지(「정기」 표시)", example = "false") boolean scheduled,
			@Schema(description = "메뉴 수", example = "4") long optionCount,
			@Schema(description = "응답 수(패스 포함)", example = "8") long responseCount) {
	}

	@Schema(name = "AdminBlock", description = "차단한 구글 계정(DA07) 한 줄. 강제 탈퇴할 때 기록된다")
	record Block(
			@Schema(description = "차단 기록 ID", example = "3") Long id,
			@Schema(description = "차단한 구글 계정 이메일", example = "kim@example.com") String email,
			@Schema(description = "차단할 때의 구글 계정 이름", example = "김오점") String name,
			@Schema(description = "차단 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant blockedAt,
			@Schema(description = "차단한 관리자의 화면 이름. 그 관리자 계정이 없어졌으면 null", example = "관리자", nullable = true)
			String blockedByName) {
	}
}
