package com.ohjumwhat.organization;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memberships")
public class Membership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long organizationId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Instant joinedAt;

	/** 로그인 후 "최근 들어간 조직"으로 보낼 때 쓴다. */
	private Instant lastVisitedAt;

	protected Membership() {
	}

	public Membership(Long organizationId, Long userId, Instant joinedAt) {
		this.organizationId = organizationId;
		this.userId = userId;
		this.joinedAt = joinedAt;
		this.lastVisitedAt = joinedAt;
	}

	public void visit(Instant now) {
		this.lastVisitedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public Long getUserId() {
		return userId;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public Instant getLastVisitedAt() {
		return lastVisitedAt;
	}
}
