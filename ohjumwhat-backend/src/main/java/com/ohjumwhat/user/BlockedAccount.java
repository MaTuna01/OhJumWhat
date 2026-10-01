package com.ohjumwhat.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 관리자가 강제 탈퇴시킨 구글 계정. 같은 계정으로 다시 로그인하면 거절한다. 행을 지우면 차단이 풀린다. */
@Entity
@Table(name = "blocked_accounts")
public class BlockedAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String googleSub;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String name;

	/** 차단한 관리자. 그 관리자가 삭제되면 NULL */
	private Long blockedBy;

	@Column(nullable = false)
	private Instant blockedAt;

	protected BlockedAccount() {
	}

	public BlockedAccount(User user, Long blockedBy, Instant blockedAt) {
		this.googleSub = user.getGoogleSub();
		this.email = user.getEmail();
		this.name = user.getName();
		this.blockedBy = blockedBy;
		this.blockedAt = blockedAt;
	}

	public Long getId() {
		return id;
	}

	public String getGoogleSub() {
		return googleSub;
	}
}
