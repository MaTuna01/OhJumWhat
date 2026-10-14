package com.ohjumwhat.common;

import java.util.List;

import io.swagger.v3.oas.models.tags.Tag;

/**
 * API 명세(Swagger)의 묶음. 컨트롤러는 {@code @Tag(name = ApiTags.POLL)}처럼 이 이름을 단다.
 * 화면에 보이는 순서와 설명(쓰는 화면, Figma 프레임)은 {@link #ALL}이 정한다(OpenApiConfig).
 */
public final class ApiTags {

	public static final String CONFIG = "설정";

	public static final String ME = "내 정보·프로필";

	public static final String ORG = "조직";

	public static final String INVITE = "초대";

	public static final String POLL = "투표";

	public static final String COMMENT = "메뉴 댓글";

	public static final String CHAT = "투표 채팅";

	public static final String PLACE = "식당·지도";

	public static final String SCHEDULE = "정기 투표";

	public static final String STATS = "통계·추천";

	public static final String RANKING = "랭킹";

	public static final String NOTICE = "새 소식";

	public static final String LETTER = "쪽지";

	public static final String GUESTBOOK = "방명록";

	public static final String PUSH = "알림 기기";

	public static final String SANCTION = "이용 제한·신고";

	public static final String ADMIN_OVERVIEW = "관리자 · 개요";

	public static final String ADMIN_USER = "관리자 · 회원";

	public static final String ADMIN_ORG = "관리자 · 조직·투표";

	public static final String ADMIN_REPORT = "관리자 · 신고";

	public static final String ADMIN_SANCTION = "관리자 · 제재";

	public static final String ADMIN_BLOCK = "관리자 · 차단";

	public static final String ADMIN_NOTICE = "관리자 · 공지";

	/** 명세에 보이는 순서. 설명에는 그 API를 쓰는 화면(Figma 데스크톱 프레임)을 적는다. */
	static final List<Tag> ALL = List.of(
			tag(CONFIG, "화면이 처음 뜰 때 받는 설정(지도 키·웹 푸시 공개 값). 모든 화면"),
			tag(ME, "D03 마이페이지: 내 정보, 별명·사진·소개·상세 프로필 수정(03-M2)"),
			tag(ORG, "D04 조직 홈, D07 조직 설정(이름·위치·멤버·탈퇴), 조직 만들기"),
			tag(INVITE, "D02 초대 수락, D07 초대 링크"),
			tag(POLL, "D04 조직 홈(오늘·지난 투표), D05 투표 상세(메뉴·식당·참여·투표 관리)"),
			tag(COMMENT, "D05 투표 상세의 메뉴 댓글(D05-K)"),
			tag(CHAT, "D05 투표 상세의 채팅(D05-C). 받기는 WebSocket `/api/polls/{pollId}/ws`(문서 첫머리 참고)"),
			tag(PLACE, "D05 근처 식당 찾기(05-F)·투표 지도(D05-G), D07 조직 위치(D07-L)"),
			tag(SCHEDULE, "D06 정기 투표 관리"),
			tag(STATS, "D08 통계, D05 메뉴 입력창의 추천(05-R)"),
			tag(RANKING, "D11 메뉴 채택 랭킹, D04 조직 홈 랭킹 카드(D04-R)"),
			tag(NOTICE, "D09 새 소식, 상단 바 종·조직 홈 배너"),
			tag(LETTER, "D10 쪽지함, 상단 바 쪽지 배지"),
			tag(GUESTBOOK, "D07 멤버 프로필의 방명록(D07-P4), D03 마이페이지 방명록(D03-N5)"),
			tag(PUSH, "D03 마이페이지 「알림」 카드(D03-N6)의 웹 푸시 기기 등록"),
			tag(SANCTION, "이용 제한 안내(00-S)·마이페이지 「이용 제한」(D03-N7), 사람 신고(07-M5)와 결과(00-R)"),
			tag(ADMIN_OVERVIEW, "관리자 콘솔 개요(DA01)"),
			tag(ADMIN_USER, "관리자 콘솔 회원 목록·상세·강제 탈퇴·프로필 수정"),
			tag(ADMIN_ORG, "관리자 콘솔 조직·투표·메뉴·댓글·채팅·정기 투표"),
			tag(ADMIN_REPORT, "관리자 콘솔 「신고」 탭(쪽지·방명록·프로필)"),
			tag(ADMIN_SANCTION, "관리자 콘솔 이용 제한(제재하기·해제·「제재」 탭)"),
			tag(ADMIN_BLOCK, "관리자 콘솔 「차단」 탭(강제 탈퇴한 구글 계정)"),
			tag(ADMIN_NOTICE, "관리자 콘솔 「공지」(개발자 노트)"));

	private ApiTags() {
	}

	private static Tag tag(String name, String description) {
		return new Tag().name(name).description(description);
	}
}
