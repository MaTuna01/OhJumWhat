package com.ohjumwhat.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String googleSub;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String name;

	private String profileImageUrl;

	// DB 기본값은 Hibernate가 INSERT에 컬럼을 넣으므로 쓰이지 않는다. 여기서 기본값을 둔다.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role = Role.USER;

	private Instant lastLoginAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String googleSub, String email, String name, String profileImageUrl) {
		this.googleSub = googleSub;
		this.email = email;
		this.name = name;
		this.profileImageUrl = profileImageUrl;
	}

	/** 구글 로그인 때마다 최신 프로필로 갱신한다. */
	public void updateProfile(String email, String name, String profileImageUrl) {
		this.email = email;
		this.name = name;
		this.profileImageUrl = profileImageUrl;
	}

	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
	}

	public void promote() {
		this.role = Role.ADMIN;
	}

	public void demote() {
		this.role = Role.USER;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getGoogleSub() {
		return googleSub;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public Role getRole() {
		return role;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
