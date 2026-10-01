package com.ohjumwhat.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.ohjumwhat.poll.PollStatus;
import com.ohjumwhat.schedule.ScheduleResponse;
import com.ohjumwhat.user.Role;

/** 관리자 콘솔 응답. 목록 행은 AdminRepository의 JPQL에서 바로 만든다. */
final class AdminResponses {

	private AdminResponses() {
	}

	/** 개요: 전체 회원·조직 수, 오늘 투표 수, 지금 진행 중인 투표 수, 최근 7일 가입자 수, 차단 수 */
	record Stats(long userCount, long organizationCount, long todayPollCount, long openPollCount,
			long newUserCount, long blockedCount) {
	}

	/** name은 화면 이름(별명, 없으면 구글 이름), googleName은 구글 계정 이름 */
	record UserRow(Long id, String name, String googleName, String email, String profileImageUrl, Role role,
			Instant createdAt, Instant lastLoginAt, long organizationCount) {
	}

	/**
	 * @param lastAccessAt 세션의 마지막 요청 시각(로그인은 30일 유지되므로 최근 로그인보다 최근 활동에 가깝다)
	 */
	record UserDetail(UserRow user, Instant lastAccessAt, List<UserOrganization> organizations, Activity activity) {
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
	}

	/** 조직의 최근 투표. status는 서비스에서 지금 시각으로 정한다. */
	record PollRow(Long id, String title, LocalDate pollDate, Instant closesAt, PollStatus status, boolean scheduled,
			long optionCount, long responseCount) {
	}

	record Block(Long id, String email, String name, Instant blockedAt, String blockedByName) {
	}
}
