package com.ohjumwhat.organization;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.place.PlaceLink;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.vote.VoteRepository;

@Slf4j
@Service
public class OrganizationService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final OrganizationRepository organizationRepository;

	private final MembershipRepository membershipRepository;

	private final MembershipService membershipService;

	private final PollRepository pollRepository;

	private final VoteRepository voteRepository;

	private final Clock clock;

	public OrganizationService(OrganizationRepository organizationRepository,
			MembershipRepository membershipRepository, MembershipService membershipService,
			PollRepository pollRepository, VoteRepository voteRepository, Clock clock) {
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.membershipService = membershipService;
		this.pollRepository = pollRepository;
		this.voteRepository = voteRepository;
		this.clock = clock;
	}

	/** 조직을 만들고 만든 사람을 첫 멤버로 넣는다. */
	@Transactional
	public OrganizationResponse create(Long userId, String name) {
		Organization organization = organizationRepository.save(new Organization(name.strip(), newInviteToken()));
		membershipRepository.save(new Membership(organization.getId(), userId, Instant.now(clock)));
		log.info("조직 생성: organizationId={}, userId={}", organization.getId(), userId);
		return toResponse(organization, 1);
	}

	/** 조직 홈에 들어올 때 호출되며, "최근 들어간 조직"을 갱신한다. */
	@Transactional
	public OrganizationResponse visit(Long organizationId, Long userId) {
		Membership membership = membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
			.orElseThrow(() -> ApiException.notFound(MembershipService.ORGANIZATION_NOT_FOUND));
		membership.visit(Instant.now(clock));
		Organization organization = organizationRepository.findById(organizationId).orElseThrow();
		return toResponse(organization, membershipRepository.countByOrganizationId(organizationId));
	}

	@Transactional
	public OrganizationResponse rename(Long organizationId, Long userId, String name) {
		membershipService.requireMember(organizationId, userId);
		Organization organization = organizationRepository.findById(organizationId).orElseThrow();
		organization.rename(name.strip());
		log.info("조직 이름 변경: organizationId={}, userId={}", organizationId, userId);
		return toResponse(organization, membershipRepository.countByOrganizationId(organizationId));
	}

	/**
	 * 조직 위치 바꾸기(멤버 누구나). area는 앞뒤 공백을 지우고 비면 지운다.
	 * office는 컨트롤러가 트랜잭션 밖에서 PlaceLinkResolver로 정리한 값이다(없으면 null).
	 */
	@Transactional
	public OrganizationResponse changeLocation(Long organizationId, Long userId, String area, PlaceLink office) {
		membershipService.requireMember(organizationId, userId);
		Organization organization = organizationRepository.findById(organizationId).orElseThrow();
		organization.changeLocation(area == null || area.isBlank() ? null : area.strip(), office);
		log.info("조직 위치 변경: organizationId={}, userId={}, 지역={}, 회사={}", organizationId, userId,
				organization.getArea() != null, office != null);
		return toResponse(organization, membershipRepository.countByOrganizationId(organizationId));
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> members(Long organizationId, Long userId) {
		membershipService.requireMember(organizationId, userId);
		return membershipRepository.findMembers(organizationId);
	}

	@Transactional(readOnly = true)
	public List<MyOrganizationResponse> myOrganizations(Long userId) {
		List<MyOrganizationRow> rows = membershipRepository.findMyOrganizations(userId);
		if (rows.isEmpty()) {
			return List.of();
		}
		Instant now = Instant.now(clock);
		Set<Long> withOpenPoll = new HashSet<>(pollRepository.findOrganizationIdsWithOpenPoll(
				rows.stream().map(MyOrganizationRow::id).toList(), LocalDate.now(clock), now));
		return rows.stream()
			.map(row -> new MyOrganizationResponse(row.id(), row.name(), row.memberCount(),
					withOpenPoll.contains(row.id())))
			.toList();
	}

	/**
	 * 조직에서 탈퇴한다. 진행 중인 투표의 내 응답은 지우고 마감된 투표 기록은 남긴다.
	 * 마지막 멤버였다면 조직을 삭제하고, 하위 데이터는 DB의 ON DELETE CASCADE로 함께 지워진다.
	 */
	@Transactional
	public LeaveResponse leave(Long organizationId, Long userId) {
		Organization organization = organizationRepository.findByIdForUpdate(organizationId)
			.orElseThrow(() -> ApiException.notFound(MembershipService.ORGANIZATION_NOT_FOUND));
		Membership membership = membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
			.orElseThrow(() -> ApiException.notFound(MembershipService.ORGANIZATION_NOT_FOUND));
		return new LeaveResponse(remove(organization, membership));
	}

	/**
	 * 관리자가 멤버를 내보내거나 회원을 강제 탈퇴시킬 때 쓰는 탈퇴 처리. 규칙은 {@link #leave}와 같다.
	 * 이미 조직이나 멤버가 없으면 예외 대신 아무것도 하지 않는다(호출한 쪽의 트랜잭션을 되돌리지 않기 위해).
	 * @return 마지막 멤버라서 조직을 삭제했으면 true
	 */
	@Transactional
	public boolean removeMember(Long organizationId, Long userId) {
		return organizationRepository.findByIdForUpdate(organizationId)
			.flatMap(organization -> membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
				.map(membership -> remove(organization, membership)))
			.orElse(false);
	}

	/** 조직 행을 잠근 상태에서 호출한다. */
	private boolean remove(Organization organization, Membership membership) {
		Long organizationId = organization.getId();
		Long userId = membership.getUserId();
		int deletedVotes = voteRepository.deleteInOpenPolls(organizationId, userId, Instant.now(clock));
		membershipRepository.delete(membership);
		membershipRepository.flush();
		log.info("조직 탈퇴: organizationId={}, userId={}, 삭제한 응답 수={}", organizationId, userId, deletedVotes);

		if (membershipRepository.countByOrganizationId(organizationId) == 0) {
			organizationRepository.delete(organization);
			log.info("마지막 멤버 탈퇴로 조직 삭제: organizationId={}", organizationId);
			return true;
		}
		return false;
	}

	private static OrganizationResponse toResponse(Organization organization, long memberCount) {
		return new OrganizationResponse(organization.getId(), organization.getName(), organization.getInviteToken(),
				memberCount, organization.getArea(), organization.getOfficeName(), organization.getOfficeLinkUrl());
	}

	/** 초대 링크용 토큰: 32바이트 난수를 URL에 안전한 base64로 인코딩(43자) */
	private static String newInviteToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
