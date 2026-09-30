package com.ohjumwhat.organization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;

/** 조직 하위 API는 모두 여기서 멤버인지 먼저 확인한다. */
@Service
public class MembershipService {

	static final String ORGANIZATION_NOT_FOUND = "조직을 찾을 수 없어요.";

	private final MembershipRepository membershipRepository;

	public MembershipService(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	/** 멤버가 아니면 조직의 존재 여부도 알리지 않도록 404로 응답한다. */
	@Transactional(readOnly = true)
	public void requireMember(Long organizationId, Long userId) {
		if (!membershipRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
			throw ApiException.notFound(ORGANIZATION_NOT_FOUND);
		}
	}
}
