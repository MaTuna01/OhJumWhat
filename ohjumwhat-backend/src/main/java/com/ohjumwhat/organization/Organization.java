package com.ohjumwhat.organization;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.ohjumwhat.place.PlaceLink;

@Entity
@Table(name = "organizations")
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String inviteToken;

	/** 검색 지역(예: 역삼동). 「네이버 지도에서 찾기」 검색어 앞에 붙인다. 없으면 null */
	private String area;

	/** 회사 위치 이름(선택). 회사 지도 링크가 있을 때만 둔다. */
	private String officeName;

	/** 회사 위치 지도 링크(선택). 네이버 장소면 정식 링크 */
	private String officeLinkUrl;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Organization() {
	}

	public Organization(String name, String inviteToken) {
		this.name = name;
		this.inviteToken = inviteToken;
	}

	public void rename(String name) {
		this.name = name;
	}

	/** 조직 위치를 통째로 바꾼다. 빈 값은 지운다(office가 null이면 회사 위치를 지운다). */
	public void changeLocation(String area, PlaceLink office) {
		this.area = area;
		this.officeLinkUrl = office == null ? null : office.url();
		this.officeName = office == null ? null : office.name();
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

	public String getName() {
		return name;
	}

	public String getInviteToken() {
		return inviteToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getArea() {
		return area;
	}

	public String getOfficeName() {
		return officeName;
	}

	public String getOfficeLinkUrl() {
		return officeLinkUrl;
	}
}
