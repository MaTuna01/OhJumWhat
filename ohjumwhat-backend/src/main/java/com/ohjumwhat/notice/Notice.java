package com.ohjumwhat.notice;

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
@Table(name = "notices")
public class Notice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NoticeKind kind;

	/** 업데이트 글의 버전(예: 1.5.0). 개발자 노트는 null */
	private String version;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String body;

	/** 개발자 노트를 쓴 관리자. 업데이트 글이거나 쓴 관리자가 탈퇴했으면 null */
	private Long createdBy;

	/** 게시 시각. 글을 고쳐도 바꾸지 않는다(다시 알리지 않는다). */
	@Column(nullable = false)
	private Instant publishedAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Notice() {
	}

	private Notice(NoticeKind kind, String version, String title, String body, Long createdBy, Instant publishedAt) {
		this.kind = kind;
		this.version = version;
		this.title = title;
		this.body = body;
		this.createdBy = createdBy;
		this.publishedAt = publishedAt;
	}

	public static Notice release(String version, String title, String body, Instant publishedAt) {
		return new Notice(NoticeKind.RELEASE, version, title, body, null, publishedAt);
	}

	public static Notice note(String title, String body, Long createdBy, Instant publishedAt) {
		return new Notice(NoticeKind.NOTE, null, title, body, createdBy, publishedAt);
	}

	/** 제목·본문만 고친다. 게시 시각은 그대로라 다시 알리지 않는다. */
	public void revise(String title, String body) {
		this.title = title;
		this.body = body;
	}

	public boolean isRelease() {
		return kind == NoticeKind.RELEASE;
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

	public NoticeKind getKind() {
		return kind;
	}

	public String getVersion() {
		return version;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}
}
