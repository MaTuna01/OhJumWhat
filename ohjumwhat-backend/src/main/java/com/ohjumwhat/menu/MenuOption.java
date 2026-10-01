package com.ohjumwhat.menu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.ohjumwhat.place.PlaceLink;

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

	/** 식당 지도 링크(선택, http/https. 네이버 장소면 정식 링크). 추가한 사람이 투표 진행 중에 달거나 고친다. */
	private String linkUrl;

	/** 식당 이름(선택). 링크가 있을 때만 둔다. */
	private String placeName;

	@Column(nullable = false)
	private Instant createdAt;

	protected MenuOption() {
	}

	public MenuOption(Long pollId, Long createdBy, String name) {
		this(pollId, createdBy, name, null);
	}

	public MenuOption(Long pollId, Long createdBy, String name, PlaceLink place) {
		this.pollId = pollId;
		this.createdBy = createdBy;
		this.name = name;
		changePlace(place);
	}

	/** 식당 달기·고치기. null이면 링크와 이름을 함께 지운다. */
	public void changePlace(PlaceLink place) {
		this.linkUrl = place == null ? null : place.url();
		this.placeName = place == null ? null : place.name();
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

	public String getPlaceName() {
		return placeName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
