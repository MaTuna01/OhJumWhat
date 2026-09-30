package com.ohjumwhat.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
