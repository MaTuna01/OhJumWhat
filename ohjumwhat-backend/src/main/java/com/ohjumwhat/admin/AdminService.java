package com.ohjumwhat.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.organization.LeaveResponse;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.Organization;
import com.ohjumwhat.organization.OrganizationDeletedEvent;
import com.ohjumwhat.organization.OrganizationRepository;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollDeletedEvent;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.poll.PollStatus;
import com.ohjumwhat.schedule.PollSchedule;
import com.ohjumwhat.schedule.PollScheduleRepository;
import com.ohjumwhat.schedule.ScheduleResponse;
import com.ohjumwhat.user.BlockedAccount;
import com.ohjumwhat.user.BlockedAccountRepository;
import com.ohjumwhat.user.ProfilePhotoStorage;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteRepository;

/**
 * 관리자 콘솔: 서비스 전체 조회와 강제 삭제. 권한 확인은 SecurityConfig(AdminAuthorizationManager)가 한다.
 * 지우는 작업은 모두 관리자 ID와 대상 ID를 로그로 남긴다.
 */
@Slf4j
@Service
public class AdminService {

	private static final int LIST_LIMIT = 100;

	private static final int RECENT_POLL_LIMIT = 30;

	private static final String USER_NOT_FOUND = "회원을 찾을 수 없어요.";

	private static final String ORGANIZATION_NOT_FOUND = "조직을 찾을 수 없어요.";

	private static final String POLL_NOT_FOUND = "투표를 찾을 수 없어요.";

	private final AdminRepository adminRepository;

	private final UserRepository userRepository;

	private final BlockedAccountRepository blockedAccountRepository;

	private final OrganizationRepository organizationRepository;

	private final MembershipRepository membershipRepository;

	private final OrganizationService organizationService;

	private final PollRepository pollRepository;

	private final PollService pollService;

	private final MenuOptionRepository menuOptionRepository;

	private final VoteRepository voteRepository;

	private final PollScheduleRepository scheduleRepository;

	private final JdbcTemplate jdbcTemplate;

	private final ProfilePhotoStorage photoStorage;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public AdminService(AdminRepository adminRepository, UserRepository userRepository,
			BlockedAccountRepository blockedAccountRepository, OrganizationRepository organizationRepository,
			MembershipRepository membershipRepository, OrganizationService organizationService,
			PollRepository pollRepository, PollService pollService, MenuOptionRepository menuOptionRepository,
			VoteRepository voteRepository, PollScheduleRepository scheduleRepository, JdbcTemplate jdbcTemplate,
			ProfilePhotoStorage photoStorage, ApplicationEventPublisher events, Clock clock) {
		this.adminRepository = adminRepository;
		this.userRepository = userRepository;
		this.blockedAccountRepository = blockedAccountRepository;
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.organizationService = organizationService;
		this.pollRepository = pollRepository;
		this.pollService = pollService;
		this.menuOptionRepository = menuOptionRepository;
		this.voteRepository = voteRepository;
		this.scheduleRepository = scheduleRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.photoStorage = photoStorage;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AdminResponses.Stats stats() {
		Instant now = Instant.now(clock);
		return new AdminResponses.Stats(adminRepository.countUsers(), adminRepository.countOrganizations(),
				adminRepository.countPollsOn(LocalDate.now(clock)), adminRepository.countOpenPolls(now),
				adminRepository.countUsersSince(now.minus(7, ChronoUnit.DAYS)), adminRepository.countBlocks());
	}

	// 회원

	@Transactional(readOnly = true)
	public List<AdminResponses.UserRow> users(String q) {
		return adminRepository.findUsers(keyword(q), PageRequest.of(0, LIST_LIMIT));
	}

	@Transactional(readOnly = true)
	public AdminResponses.UserDetail user(Long userId) {
		AdminResponses.UserRow row = adminRepository.findUser(userId)
			.orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		String googleSub = userRepository.findById(userId).map(User::getGoogleSub).orElseThrow();
		// 세션(spring_session, Flyway V2)의 마지막 요청 시각. principal 이름은 google sub다(LoginUser).
		Long lastAccess = jdbcTemplate.queryForObject(
				"select max(last_access_time) from spring_session where principal_name = ?", Long.class, googleSub);
		AdminResponses.Activity activity = new AdminResponses.Activity(adminRepository.countPollsCreatedBy(userId),
				adminRepository.countMenusAddedBy(userId), adminRepository.countResponsesBy(userId));
		return new AdminResponses.UserDetail(row, lastAccess == null ? null : Instant.ofEpochMilli(lastAccess),
				adminRepository.findOrganizationsOfUser(userId), activity);
	}

	/**
	 * 강제 탈퇴: 모든 조직에서 탈퇴(마지막 멤버였던 조직은 삭제) → 같은 구글 계정 차단 → 회원 삭제 → 로그인 세션 만료.
	 * 회원을 지우면 그 사람의 응답은 모두 지워지고(CASCADE), 올린 메뉴는 작성자만 비운 채 남는다(SET NULL).
	 * 올린 프로필 사진 파일은 커밋한 뒤에 지운다.
	 */
	@Transactional
	public void withdraw(Long adminId, Long userId) {
		User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		if (user.getId().equals(adminId)) {
			throw ApiException.conflict("자기 자신은 탈퇴시킬 수 없어요.");
		}
		if (user.isAdmin()) {
			throw ApiException.conflict("관리자는 탈퇴시킬 수 없어요. 먼저 서버 설정에서 관리자를 해제해 주세요.");
		}
		int deletedOrganizations = 0;
		for (Long organizationId : membershipRepository.findOrganizationIdsByUserId(userId)) {
			if (organizationService.removeMember(organizationId, userId)) {
				deletedOrganizations++;
			}
		}
		BlockedAccount block = blockedAccountRepository.save(new BlockedAccount(user, adminId, Instant.now(clock)));
		photoStorage.deleteAfterCommit(user.getPhotoKey());
		userRepository.delete(user);
		userRepository.flush();
		// 세션 저장소 API는 별도 트랜잭션으로 커밋되므로, 같은 트랜잭션에서 지우도록 SQL을 쓴다.
		int expiredSessions = jdbcTemplate.update("delete from spring_session where principal_name = ?",
				block.getGoogleSub());
		log.info("관리자 강제 탈퇴: adminId={}, userId={}, blockId={}, 삭제된 조직 수={}, 만료한 세션 수={}", adminId, userId,
				block.getId(), deletedOrganizations, expiredSessions);
	}

	/** 올린 프로필 사진 지우기(부적절한 사진 대응). 구글 사진으로 돌아가고, 파일은 커밋한 뒤에 지운다. 올린 사진이 없으면 그대로 둔다. */
	@Transactional
	public void deleteUserPhoto(Long adminId, Long userId) {
		User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		String photoKey = user.getPhotoKey();
		if (photoKey == null) {
			return;
		}
		userRepository.updatePhotoKey(userId, null);
		photoStorage.deleteAfterCommit(photoKey);
		log.info("관리자 프로필 사진 삭제: adminId={}, userId={}", adminId, userId);
	}

	@Transactional(readOnly = true)
	public List<AdminResponses.Block> blocks() {
		return adminRepository.findBlocks();
	}

	/** 차단 해제: 같은 구글 계정으로 다시 로그인하면 새 회원으로 가입된다. */
	@Transactional
	public void unblock(Long adminId, Long blockId) {
		BlockedAccount block = blockedAccountRepository.findById(blockId)
			.orElseThrow(() -> ApiException.notFound("차단 기록을 찾을 수 없어요."));
		blockedAccountRepository.delete(block);
		log.info("관리자 차단 해제: adminId={}, blockId={}", adminId, blockId);
	}

	// 조직

	@Transactional(readOnly = true)
	public List<AdminResponses.OrganizationRow> organizations(String q) {
		return adminRepository.findOrganizations(keyword(q), PageRequest.of(0, LIST_LIMIT));
	}

	@Transactional(readOnly = true)
	public AdminResponses.OrganizationDetail organization(Long organizationId) {
		AdminResponses.OrganizationRow row = adminRepository.findOrganization(organizationId)
			.orElseThrow(() -> ApiException.notFound(ORGANIZATION_NOT_FOUND));
		Instant now = Instant.now(clock);
		List<AdminResponses.PollRow> polls = adminRepository
			.findRecentPolls(organizationId, PageRequest.of(0, RECENT_POLL_LIMIT))
			.stream()
			.map(r -> new AdminResponses.PollRow((Long) r[0], (String) r[1], (LocalDate) r[2], (Instant) r[4],
					now.isBefore((Instant) r[4]) ? PollStatus.OPEN : PollStatus.CLOSED, r[5] != null, (Long) r[6],
					(Long) r[7]))
			.toList();
		List<ScheduleResponse> schedules = scheduleRepository.findByOrganizationIdOrderByOpenTimeAscIdAsc(organizationId)
			.stream()
			.map(ScheduleResponse::of)
			.toList();
		return new AdminResponses.OrganizationDetail(row, adminRepository.findMembers(organizationId), polls,
				schedules);
	}

	/** 조직 삭제. 멤버·투표·메뉴·응답·정기 투표는 DB의 ON DELETE CASCADE로 함께 지워진다. */
	@Transactional
	public void deleteOrganization(Long adminId, Long organizationId) {
		Organization organization = organizationRepository.findByIdForUpdate(organizationId)
			.orElseThrow(() -> ApiException.notFound(ORGANIZATION_NOT_FOUND));
		organizationRepository.delete(organization);
		events.publishEvent(new OrganizationDeletedEvent(organizationId));
		log.info("관리자 조직 삭제: adminId={}, organizationId={}", adminId, organizationId);
	}

	/** 멤버 내보내기. 탈퇴와 같은 규칙이고, 마지막 멤버였다면 조직이 삭제된다. 초대 링크로는 다시 들어올 수 있다. */
	@Transactional
	public LeaveResponse removeMember(Long adminId, Long organizationId, Long userId) {
		if (!organizationRepository.existsById(organizationId)) {
			throw ApiException.notFound(ORGANIZATION_NOT_FOUND);
		}
		if (!membershipRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
			throw ApiException.notFound("이 조직의 멤버가 아니에요.");
		}
		boolean organizationDeleted = organizationService.removeMember(organizationId, userId);
		log.info("관리자 멤버 내보내기: adminId={}, organizationId={}, userId={}, 조직 삭제={}", adminId, organizationId,
				userId, organizationDeleted);
		return new LeaveResponse(organizationDeleted);
	}

	// 투표·메뉴·정기 투표

	/** 관리자는 멤버가 아니어도 볼 수 있다. 내 응답 칸은 관리자 기준이라 화면에서 쓰지 않는다. */
	@Transactional(readOnly = true)
	public PollDetailResponse poll(Long adminId, Long pollId) {
		return pollService.detail(findPoll(pollId), adminId);
	}

	/**
	 * 투표 삭제. 메뉴와 응답은 CASCADE로 지워진다.
	 * 정기 투표로 열린 진행 중 투표는 지워도 스케줄러가 곧 다시 열기 때문에, withSchedule이면 규칙도 함께 지운다.
	 */
	@Transactional
	public void deletePoll(Long adminId, Long pollId, boolean withSchedule) {
		Poll poll = findPoll(pollId);
		pollRepository.delete(poll);
		events.publishEvent(new PollDeletedEvent(pollId));
		if (withSchedule && poll.getScheduleId() != null) {
			scheduleRepository.deleteById(poll.getScheduleId());
		}
		log.info("관리자 투표 삭제: adminId={}, pollId={}, 정기 투표 규칙도 삭제={}", adminId, pollId,
				withSchedule && poll.getScheduleId() != null);
	}

	/** 메뉴 강제 삭제: 그 메뉴에 참여한 사람의 응답부터 지운다(그 사람들은 미응답이 된다). */
	@Transactional
	public PollDetailResponse deleteMenuOption(Long adminId, Long optionId) {
		MenuOption option = menuOptionRepository.findById(optionId)
			.orElseThrow(() -> ApiException.notFound("메뉴를 찾을 수 없어요."));
		int deletedVotes = voteRepository.deleteByOptionId(optionId);
		menuOptionRepository.delete(option);
		menuOptionRepository.flush();
		log.info("관리자 메뉴 삭제: adminId={}, pollId={}, optionId={}, 삭제한 응답 수={}", adminId, option.getPollId(),
				optionId, deletedVotes);
		return pollService.detail(findPoll(option.getPollId()), adminId);
	}

	/** 정기 투표 규칙 삭제. 이미 열린 투표는 남는다(polls.schedule_id만 NULL). */
	@Transactional
	public void deleteSchedule(Long adminId, Long scheduleId) {
		PollSchedule schedule = scheduleRepository.findById(scheduleId)
			.orElseThrow(() -> ApiException.notFound("정기 투표 규칙을 찾을 수 없어요."));
		scheduleRepository.delete(schedule);
		log.info("관리자 정기 투표 규칙 삭제: adminId={}, organizationId={}, scheduleId={}", adminId,
				schedule.getOrganizationId(), scheduleId);
	}

	private Poll findPoll(Long pollId) {
		return pollRepository.findById(pollId).orElseThrow(() -> ApiException.notFound(POLL_NOT_FOUND));
	}

	/** 검색어: 앞뒤 공백을 지우고, LIKE 특수문자는 뺀다. 없으면 빈 문자열(전체) */
	private static String keyword(String q) {
		return q == null ? "" : q.strip().replace("%", "").replace("_", "");
	}
}
