package com.ohjumwhat.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.ohjumwhat.poll.PollStatus;
import com.ohjumwhat.schedule.ScheduleResponse;
import com.ohjumwhat.user.ProfileDetailsResponse;
import com.ohjumwhat.user.Role;
import com.ohjumwhat.user.User;

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
	record Stats(long userCount, long organizationCount, long todayPollCount, long openPollCount,
			long newUserCount, long blockedCount, long openReportCount, long openLetterReportCount,
			long openGuestbookReportCount, long openProfileReportCount, long restrictedUserCount) {
	}

	/**
	 * name은 화면 이름(별명, 없으면 구글 이름), googleName은 구글 계정 이름.
	 * profileImageUrl은 화면 사진(올린 사진, 없으면 구글 사진), customPhoto는 올린 사진이 있는지.
	 * restricted는 지금 진행 중인 제재가 있는지(「제한 중」 배지, AdminService가 한 번에 조회해 채운다)
	 */
	record UserRow(Long id, String name, String googleName, String email, String profileImageUrl,
			boolean customPhoto, Role role, Instant createdAt, Instant lastLoginAt, long organizationCount,
			boolean restricted) {

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
	record UserDetail(UserRow user, String nickname, String bio, List<String> foodTags,
			ProfileDetailsResponse details, Instant lastAccessAt, List<UserOrganization> organizations,
			Activity activity, long openProfileReportCount) {
	}

	record UserOrganization(Long id, String name, long memberCount, Instant joinedAt, Instant lastVisitedAt) {
	}

	/** 그 회원이 만든 투표, 올린 메뉴, 남긴 응답(패스 포함) 수 */
	record Activity(long pollsCreated, long menusAdded, long responses) {
	}

	record OrganizationRow(Long id, String name, Instant createdAt, long memberCount, long pollCount,
			LocalDate lastPollDate) {
	}

	record OrganizationDetail(OrganizationRow organization, List<Member> members, List<PollRow> polls,
			List<ScheduleResponse> schedules) {
	}

	record Member(Long userId, String name, String email, String profileImageUrl, Role role, Instant joinedAt,
			Instant lastVisitedAt) {

		/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정한다. */
		Member(Long userId, String name, String email, String photoKey, String googleUrl, Role role, Instant joinedAt,
				Instant lastVisitedAt) {
			this(userId, name, email, User.photoUrl(photoKey, googleUrl), role, joinedAt, lastVisitedAt);
		}
	}

	/** 조직의 최근 투표. status는 서비스에서 지금 시각으로 정한다. */
	record PollRow(Long id, String title, LocalDate pollDate, Instant closesAt, PollStatus status, boolean scheduled,
			long optionCount, long responseCount) {
	}

	record Block(Long id, String email, String name, Instant blockedAt, String blockedByName) {
	}
}
