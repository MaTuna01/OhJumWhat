package com.ohjumwhat.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * 「새 소식」을 본 시각만 바꾼다. 엔티티 전체를 저장하면 같은 순간의 로그인·별명 변경을 덮어쓸 수 있어서
	 * 이 컬럼만 고친다. 바뀐 행 수(회원이 없으면 0)를 돌려준다.
	 */
	@Modifying
	@Query("update User u set u.noticesSeenAt = :seenAt where u.id = :id")
	int markNoticesSeen(Long id, Instant seenAt);
}
