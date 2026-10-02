package com.ohjumwhat.place;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * 지도에 올릴 위치를 카카오 로컬로 찾는다. 네트워크를 쓰므로 트랜잭션을 걸지 않고, DB는 짧은 조회만 한다.
 * 결과(좌표)는 약관상 저장·캐시하지 않고 응답에만 쓴다. 멤버인지 먼저 확인해서 멤버가 아니면 카카오를 부르지 않는다.
 */
@Slf4j
@Service
public class PlaceSearchService {

	static final int MAX_OPTIONS = 30;

	/** 위치 찾기 전체 마감. 넘긴 항목은 빼고 찾은 것만 준다. */
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
	 * 회사 위치와 메뉴(optionIds)별 식당 위치. 이 조직의 메뉴만 보고, 주소가 없거나 찾지 못한 메뉴는 뺀다.
	 * optionIds가 비면 회사 위치만 준다(조직 설정의 지도 미리보기).
	 */
	public PlacesResponse places(Long organizationId, Long userId, List<Long> optionIds) {
		membershipService.requireMember(organizationId, userId);
		List<Long> ids = optionIds == null ? List.of() : optionIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.size() > MAX_OPTIONS) {
			throw ApiException.badRequest("한 번에 메뉴 30개까지 볼 수 있어요.");
		}
		Organization organization = organizationRepository.findById(organizationId)
			.orElseThrow(() -> ApiException.notFound("조직을 찾을 수 없어요."));
		if (!kakaoLocal.enabled()) {
			return new PlacesResponse(null, List.of());
		}
		List<MenuOption> options = ids.isEmpty() ? List.of()
				: menuOptionRepository.findInOrganization(organizationId, ids)
					.stream()
					.filter(o -> o.getLinkUrl() != null && o.getPlaceAddress() != null)
					.toList();

		// 같은 주소는 한 번만 찾는다.
		Set<String> addresses = new LinkedHashSet<>();
		String office = organization.getOfficeAddress();
		if (office != null) {
			addresses.add(office);
		}
		options.forEach(o -> addresses.add(o.getPlaceAddress()));
		Map<String, Coordinate> found = geocodeAll(addresses);

		Coordinate officeAt = office == null ? null : found.get(office);
		PlacesResponse.Center center = officeAt == null ? null
				: new PlacesResponse.Center(officeAt.lat(), officeAt.lng(), organization.getOfficeName());
		List<PlacesResponse.Spot> spots = new ArrayList<>();
		for (MenuOption option : options) {
			Coordinate at = found.get(option.getPlaceAddress());
			if (at != null) {
				spots.add(new PlacesResponse.Spot(option.getId(), at.lat(), at.lng()));
			}
		}
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

	/** 주소별 좌표(가상 스레드로 함께 찾는다). 못 찾았거나, 실패했거나, 마감을 넘긴 주소는 빠진다. */
	private Map<String, Coordinate> geocodeAll(Collection<String> queries) {
		if (queries.isEmpty()) {
			return Map.of();
		}
		List<String> list = List.copyOf(queries);
		List<Callable<Optional<Coordinate>>> tasks = list.stream()
			.<Callable<Optional<Coordinate>>>map(query -> () -> kakaoLocal.geocode(query))
			.toList();
		Map<String, Coordinate> result = new HashMap<>();
		int failed = 0;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Optional<Coordinate>>> futures = executor.invokeAll(tasks, DEADLINE.toMillis(),
					TimeUnit.MILLISECONDS);
			for (int i = 0; i < futures.size(); i++) {
				try {
					Optional<Coordinate> at = futures.get(i).get();
					if (at.isPresent()) {
						result.put(list.get(i), at.get());
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
			log.warn("위치 찾기 일부 실패: 요청={}, 실패={}", list.size(), failed);
		}
		return result;
	}
}
