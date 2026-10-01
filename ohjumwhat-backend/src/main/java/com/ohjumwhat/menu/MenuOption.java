package com.ohjumwhat.menu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu_options")
public class MenuOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pollId;

	// 추가한 사람이 강제 탈퇴로 삭제되면 NULL이 된다(메뉴는 남는다).
	private Long createdBy;

	@Column(nullable = false)
	private String name;

	/** 식당 지도 링크(선택, http/https). 추가한 사람이 투표 진행 중에 달거나 고친다. */
	private String linkUrl;

	@Column(nullable = false)
	private Instant createdAt;

	protected MenuOption() {
	}

	public MenuOption(Long pollId, Long createdBy, String name) {
		this(pollId, createdBy, name, null);
	}

	public MenuOption(Long pollId, Long createdBy, String name, String linkUrl) {
		this.pollId = pollId;
		this.createdBy = createdBy;
		this.name = name;
		this.linkUrl = linkUrl;
	}

	/** null이면 링크를 지운다. */
	public void changeLink(String linkUrl) {
		this.linkUrl = linkUrl;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getPollId() {
		return pollId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public String getName() {
		return name;
	}

	public String getLinkUrl() {
		return linkUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
