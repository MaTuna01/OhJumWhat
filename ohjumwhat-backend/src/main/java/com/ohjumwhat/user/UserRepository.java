package com.ohjumwhat.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByGoogleSub(String googleSub);

	List<User> findByRole(Role role);

	boolean existsByIdAndRole(Long id, Role role);

	@Query("select u from User u where lower(u.email) in :emails")
	List<User> findByEmailIn(List<String> emails);

	/** 강제 탈퇴 중에 같은 회원의 다른 변경(로그인, 다른 관리자의 처리)과 겹치지 않도록 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(Long id);
}
