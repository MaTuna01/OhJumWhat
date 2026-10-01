package com.ohjumwhat.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedAccountRepository extends JpaRepository<BlockedAccount, Long> {

	Optional<BlockedAccount> findByGoogleSub(String googleSub);
}
