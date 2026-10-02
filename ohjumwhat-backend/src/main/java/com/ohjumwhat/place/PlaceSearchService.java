package com.ohjumwhat.place;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.organization.MembershipService;
import com.ohjumwhat.organization.Organization;
import com.ohjumwhat.organization.OrganizationRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 지도에 올릴 위치와 근처 식당을 카카오 로컬로 찾는다. 네트워크를 쓰므로 트랜잭션을 걸지 않고, DB는 짧은 조회만 한다.
 * 결과(좌표·이름·주소)는 약관상 저장·캐시하지 않고 응답에만 쓴다. 멤버인지 먼저 확인해서 멤버가 아니면 카카오를 부르지 않는다.
 */
@Slf4j
@Service
public class PlaceSearchService {

	static final int MAX_OPTIONS = 30;

	static final int MAX_QUERY_LENGTH = 50;

	/** 카카오는 검색어 하나에 45개(15개씩 3페이지)까지만 준다. */
	static final int MAX_PAGE = 3;

	/**
	 * 카카오 식당을 다시 찾을 때의 반경. 거리순이라 반경이 달라도 가까운 45개는 같으므로, 조직의 검색 반경을 바꿔도 지난 식당을 계속 찾는다.
	 */
	static final int RESOLVE_RADIUS = 20_000;

	/** 위치 찾기 한 단계(주소 → 좌표, 카카오 식당 다시 찾기)의 마감. 넘긴 항목은 빼고 찾은 것만 준다. */
	private static final Duration DEADLINE = Duration.ofSeconds(4);

	private final KakaoLocal kakaoLocal;

	private final MembershipService membershipService;

	private final OrganizationRepository organizationRepository;

	private final MenuOptionRepository menuOptionRepository;

	public PlaceSearchService(KakaoLocal kakaoLocal, MembershipService membershipService,
			OrganizationRepository organizationRepository, MenuOptionRepository menuOptionRepository) {
		this.kakaoLocal = kakaoLocal;
		this.membershipService = membershipService;
		this.organizationRepository = organizationRepository;
		this.menuOptionRepository = menuOptionRepository;
	}

	/** 지도·근처 식당 찾기를 쓸 수 있는지(카카오 키가 있는지) */
	public boolean enabled() {
		return kakaoLocal.enabled();
	}

	/**
	 * 근처 식당 찾기: 회사 주소 기준 검색 반경 안 음식점을 가까운 순으로 15개씩(page 1~3).
	 * query가 비면 근처 음식점을 모두 둘러본다.
	 */
	public PlaceSearchResponse search(Long organizationId, Long userId, String rawQuery, int page) {
		membershipService.requireMember(organizationId, userId);
		if (page < 1 || page > MAX_PAGE) {
			throw ApiException.badRequest("더 볼 식당이 없어요.");
		}
		String query = PlaceLinks.query(rawQuery);
		if (query != null && query.length() > MAX_QUERY_LENGTH) {
			throw ApiException.badRequest("검색어는 50자 이하로 입력해 주세요.");
		}
		Organization organization = organization(organizationId);
		if (!kakaoLocal.enabled()) {
			throw ApiException.unavailable("근처 식당 찾기를 쓸 수 없어요. 링크를 붙여 주세요.");
		}
		if (organization.getOfficeAddress() == null) {
			throw ApiException.badRequest("조직 설정에서 회사 주소를 정하면 근처 식당을 찾을 수 있어요.");
		}
		try {
			Coordinate center = kakaoLocal.geocode(organization.getOfficeAddress())
				.orElseThrow(() -> ApiException.badRequest("회사 주소를 지도에서 찾지 못했어요. 조직 설정에서 주소를 확인해 주세요."));
			KakaoPlace.Page result = kakaoLocal.searchRestaurants(query, center, organization.getSearchRadius(), page);
			List<PlaceSearchResponse.Place> places = result.places()
				.stream()
				.map(p -> new PlaceSearchResponse.Place(p.id(), p.name(), p.category(), p.roadAddress(), p.at().lat(),
						p.at().lng(), p.distance()))
				.toList();
			return new PlaceSearchResponse(
					new PlacesResponse.Center(center.lat(), center.lng(), organization.getOfficeName()), places,
					!result.end() && page < MAX_PAGE);
		}
		catch (PlaceSearchUnavailableException e) {
			log.warn("근처 식당 찾기 실패: organizationId={}", organizationId);
			throw ApiException.unavailable("식당 검색이 잠시 안 돼요. 잠시 뒤에 다시 해 주세요.");
		}
	}

	/**
	 * 회사 위치와 메뉴(optionIds)별 식당 위치. 이 조직의 메뉴만 보고, 찾지 못한 메뉴는 뺀다. optionIds가 비면 회사 위치만 준다.
	 * <ul>
	 * <li>링크로 붙인 식당: 저장한 주소(사용자 입력)를 좌표로 바꾼다.</li>
	 * <li>근처 식당 찾기로 고른 카카오 식당: 회사 좌표를 기준으로 저장한 검색어로 다시 찾아 장소 ID가 같은 결과의 이름·주소·위치를 쓴다.</li>
	 * </ul>
	 */
	public PlacesResponse places(Long organizationId, Long userId, List<Long> optionIds) {
		membershipService.requireMember(organizationId, userId);
		List<Long> ids = optionIds == null ? List.of() : optionIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.size() > MAX_OPTIONS) {
			throw ApiException.badRequest("한 번에 메뉴 30개까지 볼 수 있어요.");
		}
		Organization organization = organization(organizationId);
		if (!kakaoLocal.enabled()) {
			return new PlacesResponse(null, List.of());
		}
		List<MenuOption> options = ids.isEmpty() ? List.of()
				: menuOptionRepository.findInOrganization(organizationId, ids)
					.stream()
					.filter(o -> o.getLinkUrl() != null)
					.toList();

		// 1단계: 주소 → 좌표(회사, 링크로 붙인 식당). 같은 주소는 한 번만 찾는다.
		Set<String> addresses = new LinkedHashSet<>();
		String office = organization.getOfficeAddress();
		if (office != null) {
			addresses.add(office);
		}
		options.stream()
			.filter(o -> o.getKakaoPlaceId() == null && o.getPlaceAddress() != null)
			.forEach(o -> addresses.add(o.getPlaceAddress()));
		Map<String, Callable<Coordinate>> geocodes = new LinkedHashMap<>();
		addresses.forEach(address -> geocodes.put(address, () -> kakaoLocal.geocode(address).orElse(null)));
		Map<String, Coordinate> found = runAll(geocodes);
		Coordinate officeAt = office == null ? null : found.get(office);

		// 2단계: 카카오 식당 다시 찾기(회사 좌표가 있어야 한다). 같은 검색어끼리 한 번에 찾는다.
		Map<String, KakaoPlace> kakaoPlaces = officeAt == null ? Map.of() : resolveKakao(options, officeAt);

		List<PlacesResponse.Spot> spots = new ArrayList<>();
		for (MenuOption option : options) {
			if (option.getKakaoPlaceId() != null) {
				KakaoPlace place = kakaoPlaces.get(option.getKakaoPlaceId());
				if (place != null) {
					spots.add(new PlacesResponse.Spot(option.getId(), place.at().lat(), place.at().lng(), place.name(),
							place.category(), place.roadAddress()));
				}
			}
			else if (option.getPlaceAddress() != null) {
				Coordinate at = found.get(option.getPlaceAddress());
				if (at != null) {
					spots.add(new PlacesResponse.Spot(option.getId(), at.lat(), at.lng(), null, null, null));
				}
			}
		}
		PlacesResponse.Center center = officeAt == null ? null
				: new PlacesResponse.Center(officeAt.lat(), officeAt.lng(), organization.getOfficeName());
		return new PlacesResponse(center, spots);
	}

	/**
	 * 회사 주소를 저장하기 전에 찾을 수 있는 주소인지 확인한다(트랜잭션 밖에서 부른다). 못 찾으면 400.
	 * 카카오를 쓸 수 없으면(키 없음·장애) 확인하지 않고 넘어간다(저장은 막지 않는다).
	 */
	public void verifyOfficeAddress(Long organizationId, Long userId, String address) {
		if (address == null || !kakaoLocal.enabled()) {
			return;
		}
		membershipService.requireMember(organizationId, userId);
		Optional<Coordinate> found;
		try {
			found = kakaoLocal.geocode(address);
		}
		catch (PlaceSearchUnavailableException e) {
			log.warn("회사 주소 확인을 건너뜀(카카오 로컬 실패): organizationId={}", organizationId);
			return;
		}
		if (found.isEmpty()) {
			throw ApiException.badRequest("주소를 찾지 못했어요. 도로명 주소로 적어 주세요.");
		}
	}

	private Organization organization(Long organizationId) {
		return organizationRepository.findById(organizationId)
			.orElseThrow(() -> ApiException.notFound("조직을 찾을 수 없어요."));
	}

	/**
	 * 카카오 식당(장소 ID → 결과). 검색어(없으면 둘러보기)별로 가까운 순 페이지를 넘기며 찾던 ID를 모두 찾거나 끝까지 본다.
	 * 고를 때 그 검색어의 가까운 45개 안에 있었으므로 대개 다시 찾는다. 회사 위치가 바뀌었으면 못 찾을 수 있다.
	 */
	private Map<String, KakaoPlace> resolveKakao(List<MenuOption> options, Coordinate center) {
		Map<Optional<String>, Set<String>> idsByQuery = new LinkedHashMap<>();
		options.stream()
			.filter(o -> o.getKakaoPlaceId() != null)
			.forEach(o -> idsByQuery.computeIfAbsent(Optional.ofNullable(o.getPlaceQuery()), q -> new HashSet<>())
				.add(o.getKakaoPlaceId()));
		Map<Optional<String>, Callable<Map<String, KakaoPlace>>> tasks = new LinkedHashMap<>();
		idsByQuery.forEach((query, wanted) -> tasks.put(query, () -> find(query.orElse(null), wanted, center)));
		Map<String, KakaoPlace> result = new HashMap<>();
		runAll(tasks).values().forEach(result::putAll);
		return result;
	}

	private Map<String, KakaoPlace> find(String query, Set<String> wanted, Coordinate center) {
		Map<String, KakaoPlace> found = new HashMap<>();
		for (int page = 1; page <= MAX_PAGE && found.size() < wanted.size(); page++) {
			KakaoPlace.Page result = kakaoLocal.searchRestaurants(query, center, RESOLVE_RADIUS, page);
			result.places().stream().filter(p -> wanted.contains(p.id())).forEach(p -> found.put(p.id(), p));
			if (result.end()) {
				break;
			}
		}
		return found;
	}

	/** 작업을 가상 스레드로 함께 돌린다. 값이 null이거나, 실패했거나, 마감을 넘긴 작업은 결과에서 빠진다. */
	private <K, V> Map<K, V> runAll(Map<K, Callable<V>> tasks) {
		if (tasks.isEmpty()) {
			return Map.of();
		}
		List<K> keys = List.copyOf(tasks.keySet());
		List<Callable<V>> callables = keys.stream().map(tasks::get).toList();
		Map<K, V> result = new HashMap<>();
		int failed = 0;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<V>> futures = executor.invokeAll(callables, DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
			for (int i = 0; i < futures.size(); i++) {
				try {
					V value = futures.get(i).get();
					if (value != null) {
						result.put(keys.get(i), value);
					}
				}
				catch (CancellationException | ExecutionException e) {
					failed++;
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (failed > 0) {
			log.warn("위치 찾기 일부 실패: 요청={}, 실패={}", keys.size(), failed);
		}
		return result;
	}
}
