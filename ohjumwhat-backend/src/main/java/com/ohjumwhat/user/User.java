package com.ohjumwhat.user;

import java.time.Instant;
import java.util.List;

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

	/**
	 * 마이페이지(또는 관리자 콘솔)에서 정한 별명(없으면 null). 있으면 구글 이름 대신 보여준다. UserRepository.updateNickname으로만
	 * 바꾼다. photoKey처럼 엔티티를 저장할 때 쓰지 않는 컬럼이라, 같은 순간의 로그인이 옛 별명을 되써서 되돌리는 일이 없다.
	 */
	@Column(insertable = false, updatable = false, length = 20)
	private String nickname;

	/** 구글 프로필 사진 주소. 로그인 때마다 갱신한다. 화면에는 {@link #getPhotoUrl()}을 쓴다. */
	private String profileImageUrl;

	/**
	 * 직접 올린 프로필 사진의 파일 키(없으면 null). UserRepository.updatePhotoKey로만 바꾼다.
	 * 엔티티를 저장할 때 이 컬럼을 쓰지 않아, 같은 순간의 로그인(프로필 갱신)이 옛 키를 되써서 사진이 깨지는 일이 없다.
	 */
	@Column(insertable = false, updatable = false, length = 32)
	private String photoKey;

	/**
	 * 한줄 소개(없으면 null). 같은 조직 멤버가 프로필에서 본다. UserRepository.updateIntro로만 바꾼다.
	 * photoKey처럼 엔티티를 저장할 때 쓰지 않는 컬럼이라, 같은 순간의 로그인이 옛 소개를 되써서 지워지는 일이 없다.
	 */
	@Column(insertable = false, updatable = false, length = 50)
	private String bio;

	/** 좋아하는 음식(직접 적는 태그, 최대 3개, 없으면 빈 배열). bio와 함께 UserRepository.updateIntro로만 바꾼다. */
	@Column(insertable = false, updatable = false)
	private String[] foodTags;

	/*
	 * 상세 프로필(V15): MBTI·퍼스널컬러·취미·나이·직급. 모두 비었거나(채우기 전) 모두 채워져 있다(DB CHECK).
	 * 소개처럼 엔티티 저장으로 쓰지 않고 UserRepository.updateDetails로만 바꾼다(같은 순간의 로그인이 되쓰지 않게).
	 */
	@Column(insertable = false, updatable = false, length = 4)
	private String mbti;

	@Enumerated(EnumType.STRING)
	@Column(insertable = false, updatable = false, length = 12)
	private PersonalColor personalColor;

	@Column(insertable = false, updatable = false)
	private String[] hobbies;

	@Column(insertable = false, updatable = false)
	private Short age;

	@Column(insertable = false, updatable = false, length = 15)
	private String jobTitle;

	// DB 기본값은 Hibernate가 INSERT에 컬럼을 넣으므로 쓰이지 않는다. 여기서 기본값을 둔다.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role = Role.USER;

	private Instant lastLoginAt;

	/**
	 * 「새 소식」을 마지막으로 본 시각(없으면 null, 가입 시각으로 본다). UserRepository.markNoticesSeen으로만 바꾼다.
	 * photoKey처럼 엔티티를 저장할 때 쓰지 않는 컬럼이라, 같은 순간의 로그인이 옛 시각을 되써서 읽음이 풀리는 일이 없다.
	 */
	@Column(insertable = false, updatable = false)
	private Instant noticesSeenAt;

	/*
	 * 방명록(V19): 내 방명록을 마지막으로 본 시각과, 제한된 내 글의 경고를 마지막으로 확인한 시각(없으면 null = 모두 새것).
	 * 엔티티 저장으로 쓰지 않고 UserRepository.markGuestbookSeen·ackGuestbookWarnings로만 바꾼다(같은 순간의 로그인이
	 * 옛 값을 되써서 본 글이 다시 새 글이 되지 않게).
	 */
	@Column(insertable = false, updatable = false)
	private Instant guestbookSeenAt;

	@Column(insertable = false, updatable = false)
	private Instant guestbookWarningsSeenAt;

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

	public String getBio() {
		return bio;
	}

	/** 좋아하는 음식(적은 순서). 가입 직후 엔티티처럼 아직 읽지 않았으면 빈 목록 */
	public List<String> getFoodTags() {
		return foodTags(foodTags);
	}

	/** DB 배열을 목록으로. JPQL로 u.foodTags를 고르는 DTO도 이것으로 바꾼다. */
	public static List<String> foodTags(String[] foodTags) {
		return tagList(foodTags);
	}

	/** 태그 배열(좋아하는 음식, 취미)을 목록으로. 아직 읽지 않았으면(null) 빈 목록 */
	public static List<String> tagList(String[] tags) {
		return tags == null ? List.of() : List.of(tags);
	}

	/** 상세 프로필. 채우지 않았으면 null */
	public ProfileDetailsResponse getDetails() {
		return ProfileDetailsResponse.of(mbti, personalColor, hobbies, age, jobTitle);
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

	public Instant getGuestbookSeenAt() {
		return guestbookSeenAt;
	}

	public Instant getGuestbookWarningsSeenAt() {
		return guestbookWarningsSeenAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
