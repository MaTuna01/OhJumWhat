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

	/** 올린 프로필 사진의 주소 앞부분 */
	public static final String PHOTO_PATH = "/api/photos/";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String googleSub;

	@Column(nullable = false)
	private String email;

	/** 구글 계정 이름. 로그인 때마다 갱신한다. 화면에는 {@link #getDisplayName()}을 쓴다. */
	@Column(nullable = false)
	private String name;

	/** 마이페이지에서 정한 별명(없으면 null). 있으면 구글 이름 대신 보여준다. */
	private String nickname;

	/** 구글 프로필 사진 주소. 로그인 때마다 갱신한다. 화면에는 {@link #getPhotoUrl()}을 쓴다. */
	private String profileImageUrl;

	/**
	 * 직접 올린 프로필 사진의 파일 키(없으면 null). UserRepository.updatePhotoKey로만 바꾼다.
	 * 엔티티를 저장할 때 이 컬럼을 쓰지 않아, 같은 순간의 로그인(프로필 갱신)이 옛 키를 되써서 사진이 깨지는 일이 없다.
	 */
	@Column(insertable = false, updatable = false, length = 32)
	private String photoKey;

	// DB 기본값은 Hibernate가 INSERT에 컬럼을 넣으므로 쓰이지 않는다. 여기서 기본값을 둔다.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role = Role.USER;

	private Instant lastLoginAt;

	/** 「새 소식」을 마지막으로 본 시각(없으면 null, 가입 시각으로 본다). UserRepository.markNoticesSeen으로만 바꾼다. */
	private Instant noticesSeenAt;

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

	/** null이면 별명을 지우고 구글 이름으로 돌아간다. */
	public void changeNickname(String nickname) {
		this.nickname = nickname;
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

	public String getNickname() {
		return nickname;
	}

	/** 화면에 보여줄 이름: 별명, 없으면 구글 이름. JPQL에서는 coalesce(u.nickname, u.name) */
	public String getDisplayName() {
		return nickname != null ? nickname : name;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public String getPhotoKey() {
		return photoKey;
	}

	/** 화면에 보여줄 사진: 올린 사진, 없으면 구글 사진. JPQL에서는 photoKey와 profileImageUrl을 함께 골라 {@link #photoUrl}로 만든다. */
	public String getPhotoUrl() {
		return photoUrl(photoKey, profileImageUrl);
	}

	/** 올린 사진은 {@code /api/photos/{key}.jpg}(ProfilePhotoController)로 보낸다. 없으면 구글 사진 주소(없으면 null). */
	public static String photoUrl(String photoKey, String googleUrl) {
		return photoKey == null ? googleUrl : PHOTO_PATH + photoKey + ".jpg";
	}

	public Role getRole() {
		return role;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public Instant getNoticesSeenAt() {
		return noticesSeenAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
