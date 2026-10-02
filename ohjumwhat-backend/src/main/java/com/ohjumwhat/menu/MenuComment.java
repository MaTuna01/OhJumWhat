package com.ohjumwhat.menu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 메뉴 댓글. 투표가 진행 중일 때만 쓰고 고치고 지운다. */
@Entity
@Table(name = "menu_comments")
public class MenuComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long optionId;

	// 쓴 사람이 강제 탈퇴로 삭제되면 NULL이 된다(댓글은 남는다).
	private Long userId;

	@Column(nullable = false)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant editedAt;

	protected MenuComment() {
	}

	public MenuComment(Long optionId, Long userId, String body, Instant createdAt) {
		this.optionId = optionId;
		this.userId = userId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public void edit(String body, Instant now) {
		this.body = body;
		this.editedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getOptionId() {
		return optionId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEditedAt() {
		return editedAt;
	}
}
