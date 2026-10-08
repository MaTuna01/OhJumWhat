package com.ohjumwhat.chat;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 투표 채팅 메시지. 글 메시지(body)와 사진 메시지(imageKey, 사진 1장) 중 하나다.
 * 지우면 행은 남기고 본문·사진만 비운다("삭제된 메시지예요").
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

	/** 사진을 보여주는 기간. 지나면 「보관 기간이 지난 사진이에요」이고 파일은 정리 작업({@link ChatPhotoJanitor})이 지운다. */
	public static final Duration PHOTO_RETENTION = Duration.ofDays(30);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pollId;

	// 쓴 사람이 강제 탈퇴로 삭제되면 NULL이 된다(메시지는 남는다).
	private Long userId;

	/** 지운 메시지는 NULL */
	private String body;

	/** 사진 파일 키(글 메시지·지운 메시지는 NULL) */
	private String imageKey;

	/** 서버가 만든 원본 사진의 크기(px) */
	private Integer imageWidth;

	private Integer imageHeight;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant editedAt;

	private Instant deletedAt;

	protected ChatMessage() {
	}

	public ChatMessage(Long pollId, Long userId, String body, Instant createdAt) {
		this.pollId = pollId;
		this.userId = userId;
		this.body = body;
		this.createdAt = createdAt;
	}

	/** 사진 메시지. 크기는 서버가 다시 그린 원본의 크기다. */
	static ChatMessage photo(Long pollId, Long userId, String imageKey, int width, int height, Instant createdAt) {
		ChatMessage message = new ChatMessage(pollId, userId, null, createdAt);
		message.imageKey = imageKey;
		message.imageWidth = width;
		message.imageHeight = height;
		return message;
	}

	public void edit(String body, Instant now) {
		this.body = body;
		this.editedAt = now;
	}

	public void delete(Instant now) {
		this.body = null;
		this.imageKey = null;
		this.imageWidth = null;
		this.imageHeight = null;
		this.deletedAt = now;
	}

	public boolean isPhoto() {
		return imageKey != null;
	}

	/** 보관 기간(30일)이 지났는지. 마감처럼 요청 시각으로 판정한다. */
	static boolean isPhotoExpired(Instant createdAt, Instant now) {
		return !now.isBefore(createdAt.plus(PHOTO_RETENTION));
	}

	public boolean isDeleted() {
		return deletedAt != null;
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

	public String getImageKey() {
		return imageKey;
	}
}
