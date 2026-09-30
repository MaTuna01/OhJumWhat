package com.ohjumwhat.vote;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 투표에 대한 한 멤버의 응답. 한 사람당 한 행이며, 메뉴를 바꾸면 optionId만 바뀐다. */
@Entity
@Table(name = "votes")
public class Vote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pollId;

	@Column(nullable = false)
	private Long userId;

	/** NULL이면 "오늘은 패스" */
	private Long optionId;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Vote() {
	}

	public Vote(Long pollId, Long userId, Long optionId) {
		this.pollId = pollId;
		this.userId = userId;
		this.optionId = optionId;
	}

	public void change(Long optionId) {
		this.optionId = optionId;
	}

	public boolean isPass() {
		return optionId == null;
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

	public Long getPollId() {
		return pollId;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getOptionId() {
		return optionId;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
