package com.ohjumwhat.organization;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

	Optional<Membership> findFirstByUserIdOrderByLastVisitedAtDesc(Long userId);
}
