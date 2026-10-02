package com.ohjumwhat.place;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ohjumwhat.common.ApiException;

/**
 * 식당 지도 링크·식당 이름 규칙. 지도 서비스를 가리지 않고 http/https 주소면 받는다.
 * <ul>
 * <li>지도 앱의 공유 문구("[네이버 지도]\n할매집\nnaver.me/...")를 통째로 붙여도 링크만 꺼낸다. 네이버 지도 링크를 먼저 찾고,
 * 스킴이 없어도 된다.</li>
 * <li>장소 ID가 들어 있는 네이버 지도 링크는 정식 링크(https://map.naver.com/p/entry/place/{장소ID})로 바꾼다.
 * naver.me 단축 링크는 {@link PlaceLinkResolver}가 확인한다.</li>
 * </ul>
 * 네이버 검색 API 결과는 약관상 저장할 수 없어서 쓰지 않는다. 링크와 이름은 사용자가 붙여 넣은 값이다.
 */
public final class PlaceLinks {

	public static final int MAX_LENGTH = 500;

	public static final int NAME_MAX_LENGTH = 100;

	public static final int ADDRESS_MAX_LENGTH = 200;

	public static final int QUERY_MAX_LENGTH = 100;

	private static final Pattern KAKAO_PLACE_ID = Pattern.compile("\\d{1,20}");

	private static final String NAVER_PLACE_URL = "https://map.naver.com/p/entry/place/";

	private static final int CI = Pattern.CASE_INSENSITIVE;

	/** naver.me 단축 링크의 코드. 뒤에 붙은 한글·문장부호는 빼고 코드만 잡는다. */
	private static final Pattern NAVER_SHORT = Pattern.compile(
			"(?:https?://)?naver\\.me/([A-Za-z0-9]{4,16})(?![A-Za-z0-9])", CI);

	/** 네이버 지도·플레이스 링크(스킴이 없어도 된다) */
	private static final Pattern NAVER_MAP_IN_TEXT = Pattern.compile(
			"(?:https?://)?((?:map|place|[a-z]+\\.place)\\.naver\\.com/\\S*)", CI);

	private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+", CI);

	/** 장소 ID: map.naver.com/.../place/{id}, *.place.naver.com/{종류}/{id}, *.place.naver.com/share?id={id} */
	private static final Pattern MAP_PLACE_ID = Pattern.compile(
			"^https?://map\\.naver\\.com/[^?#]*?/place/(\\d{1,15})(?=[/?#]|$)", CI);

	private static final Pattern PLACE_PATH_ID = Pattern.compile(
			"^https?://(?:[a-z]+\\.)?place\\.naver\\.com/[a-z]+/(\\d{1,15})(?=[/?#]|$)", CI);

	private static final Pattern PLACE_SHARE_ID = Pattern.compile(
			"^https?://(?:[a-z]+\\.)?place\\.naver\\.com/share\\?(?:[^#]*&)?id=(\\d{1,15})(?=[&#]|$)", CI);

	/** 도메인에 점이 있는 http/https 주소(스킴은 대소문자 구분 없음) */
	private static final Pattern VALID = Pattern.compile(
			"^https?://[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(:\\d{1,5})?([/?#]\\S*)?$", CI);

	private static final String TRAILING_PUNCTUATION = ".,!?'\")]}>";

	private PlaceLinks() {
	}

	/**
	 * 붙여 넣은 글에서 링크를 꺼내 정리한다. 빈 값이면 null(링크 없음). 주소가 아니면 400.
	 * naver.me 단축 링크는 https://naver.me/{코드}로만 정리하고, 장소 확인은 {@link PlaceLinkResolver}가 한다.
	 */
	public static String normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw.strip();
		Matcher shortLink = NAVER_SHORT.matcher(text);
		if (shortLink.find()) {
			return "https://naver.me/" + shortLink.group(1);
		}
		Matcher naverMap = NAVER_MAP_IN_TEXT.matcher(text);
		String link;
		if (naverMap.find()) {
			link = "https://" + trimTrailing(naverMap.group(1));
		}
		else {
			Matcher url = URL_IN_TEXT.matcher(text);
			link = url.find() ? trimTrailing(url.group()) : text;
			if (!link.contains("://")) {
				link = "https://" + link;
			}
		}
		link = naverPlaceUrl(link).orElse(link);
		if (link.length() > MAX_LENGTH) {
			throw ApiException.badRequest("링크는 500자 이하로 입력해 주세요.");
		}
		if (!VALID.matcher(link).matches()) {
			throw ApiException.badRequest("링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요.");
		}
		return link;
	}

	/** {@link #normalize}로 정리한 링크가 naver.me 단축 링크면 그 코드 */
	public static Optional<String> naverShortCode(String link) {
		if (link == null) {
			return Optional.empty();
		}
		Matcher matcher = NAVER_SHORT.matcher(link);
		return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	/** 네이버 지도·플레이스 링크에 장소 ID가 있으면 정식 링크. 없으면 빈 값 */
	public static Optional<String> naverPlaceUrl(String link) {
		if (link == null) {
			return Optional.empty();
		}
		for (Pattern pattern : new Pattern[] { MAP_PLACE_ID, PLACE_PATH_ID, PLACE_SHARE_ID }) {
			Matcher matcher = pattern.matcher(link);
			if (matcher.find()) {
				return Optional.of(NAVER_PLACE_URL + matcher.group(1));
			}
		}
		return Optional.empty();
	}

	/** 식당 이름: 앞뒤 공백을 지우고 공백·줄바꿈은 한 칸으로. 비면 null, 100자를 넘으면 400 */
	public static String name(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String name = raw.strip().replaceAll("\\s+", " ");
		if (name.length() > NAME_MAX_LENGTH) {
			throw ApiException.badRequest("식당 이름은 100자 이하로 입력해 주세요.");
		}
		return name;
	}

	/** 주소(식당·회사): 앞뒤 공백을 지우고 공백·줄바꿈은 한 칸으로. 비면 null, 200자를 넘으면 400 */
	public static String address(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String address = raw.strip().replaceAll("\\s+", " ");
		if (address.length() > ADDRESS_MAX_LENGTH) {
			throw ApiException.badRequest("주소는 200자 이하로 입력해 주세요.");
		}
		return address;
	}

	/** 카카오 장소 ID(숫자만, 20자리까지). 비면 null, 숫자가 아니면 400 */
	public static String kakaoPlaceId(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String id = raw.strip();
		if (!KAKAO_PLACE_ID.matcher(id).matches()) {
			throw ApiException.badRequest("식당 정보가 올바르지 않아요. 다시 골라 주세요.");
		}
		return id;
	}

	/** 식당을 찾은 검색어: 앞뒤 공백을 지우고 공백은 한 칸으로. 비면 null, 100자를 넘으면 400 */
	public static String query(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String query = raw.strip().replaceAll("\\s+", " ");
		if (query.length() > QUERY_MAX_LENGTH) {
			throw ApiException.badRequest("검색어는 100자 이하로 입력해 주세요.");
		}
		return query;
	}

	private static String trimTrailing(String link) {
		int end = link.length();
		while (end > 0 && TRAILING_PUNCTUATION.indexOf(link.charAt(end - 1)) >= 0) {
			end--;
		}
		return link.substring(0, end);
	}
}
