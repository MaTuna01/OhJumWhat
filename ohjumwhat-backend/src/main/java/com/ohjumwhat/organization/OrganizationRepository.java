package com.ohjumwhat.organization;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	Optional<Organization> findByInviteToken(String inviteToken);

	/** 탈퇴 시 "마지막 멤버" 판단이 동시에 일어나지 않도록 조직 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Organization o where o.id = :id")
	Optional<Organization> findByIdForUpdate(Long id);
}
