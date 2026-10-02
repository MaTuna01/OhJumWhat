package com.ohjumwhat.organization;

import java.time.Instant;
import java.util.List;

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

	/** 근처 식당 검색 반경(m)으로 고를 수 있는 값. DB CHECK 제약과 같다. */
	public static final List<Integer> SEARCH_RADII = List.of(500, 1000, 2000);

	public static final int DEFAULT_SEARCH_RADIUS = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String inviteToken;

	/** 검색 지역(예: 역삼동). 「네이버 지도에서 찾기」 검색어 앞에 붙인다. 없으면 null */
	private String area;

	/** 장소 이름(선택). 장소 지도 링크가 있을 때만 둔다. */
	private String officeName;

	/** 장소 지도 링크(선택). 네이버 장소면 정식 링크 */
	private String officeLinkUrl;

	/** 조직 주소(선택). 근처 식당 검색·지도의 기준점이다. 좌표는 저장하지 않고 쓸 때마다 바꾼다. */
	private String officeAddress;

	/** 근처 식당 검색 반경(m). 엔티티 기본값을 두어 INSERT가 DB 기본값을 null로 덮지 않게 한다. */
	@Column(nullable = false)
	private int searchRadius = DEFAULT_SEARCH_RADIUS;

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

	/** 조직 위치를 통째로 바꾼다. 빈 값은 지운다(office가 null이면 장소 지도 링크·이름을 지운다). */
	public void changeLocation(String area, PlaceLink office, String officeAddress, int searchRadius) {
		this.area = area;
		this.officeLinkUrl = office == null ? null : office.url();
		this.officeName = office == null ? null : office.name();
		this.officeAddress = officeAddress;
		this.searchRadius = searchRadius;
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

	public String getOfficeAddress() {
		return officeAddress;
	}

	public int getSearchRadius() {
		return searchRadius;
	}
}
