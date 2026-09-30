package com.ohjumwhat.organization;

import java.time.Clock;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;

@Slf4j
@Service
public class InviteService {

	private static final String INVALID_INVITE = "유효하지 않은 초대 링크예요.";

	private final OrganizationRepository organizationRepository;

	private final MembershipRepository membershipRepository;

	private final Clock clock;

	public InviteService(OrganizationRepository organizationRepository, MembershipRepository membershipRepository,
			Clock clock) {
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public InviteResponse get(String token, Long userId) {
		Organization organization = findByToken(token);
		return new InviteResponse(organization.getId(), organization.getName(),
				membershipRepository.countByOrganizationId(organization.getId()),
				membershipRepository.existsByOrganizationIdAndUserId(organization.getId(), userId));
	}

	/** 초대 링크로 조직에 참여한다. 이미 멤버면 아무것도 하지 않는다. */
	@Transactional
	public Long join(String token, Long userId) {
		Organization organization = findByToken(token);
		if (!membershipRepository.existsByOrganizationIdAndUserId(organization.getId(), userId)) {
			membershipRepository.save(new Membership(organization.getId(), userId, Instant.now(clock)));
			log.info("초대 링크로 조직 참여: organizationId={}, userId={}", organization.getId(), userId);
		}
		return organization.getId();
	}

	private Organization findByToken(String token) {
		return organizationRepository.findByInviteToken(token)
			.orElseThrow(() -> ApiException.notFound(INVALID_INVITE));
	}
}
