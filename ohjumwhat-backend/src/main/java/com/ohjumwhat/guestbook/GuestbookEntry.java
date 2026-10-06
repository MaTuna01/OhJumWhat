package com.ohjumwhat.guestbook;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

/**
 * 방명록 글 한 줄. 사용자는 행을 지우지 않고 소프트 삭제한다(deletedAt). 관리자가 신고를 받아 제한하면(restrictedAt)
 * 본문을 누구에게도 내보내지 않는다(GuestbookEntryRepository가 고르지 않는다).
 * 지우기와 관리자 제한이 같은 순간에 일어나도 서로의 시각을 되쓰지 않게 바뀐 컬럼만 update한다(@DynamicUpdate).
 */
@DynamicUpdate
@Entity
@Table(name = "guestbook_entries")
public class GuestbookEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 방명록 주인. 강제 탈퇴로 회원이 지워지면 null */
	private Long ownerId;

	/** 쓴 사람. 강제 탈퇴로 회원이 지워지면 null(「탈퇴한 사용자」) */
	private Long authorId;

	@Column(nullable = false, length = 100)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant deletedAt;

	private Instant restrictedAt;

	protected GuestbookEntry() {
	}

	GuestbookEntry(Long ownerId, Long authorId, String body, Instant createdAt) {
		this.ownerId = ownerId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = createdAt;
	}

	/** 처음 지운 시각만 남긴다. */
	void delete(Instant now) {
		if (deletedAt == null) {
			deletedAt = now;
		}
	}

	/** 처음 제한한 시각만 남긴다(지운 글도 제한한다: 지워서 제재를 피하지 못하게). */
	void restrict(Instant now) {
		if (restrictedAt == null) {
			restrictedAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public Instant getRestrictedAt() {
		return restrictedAt;
	}
}
