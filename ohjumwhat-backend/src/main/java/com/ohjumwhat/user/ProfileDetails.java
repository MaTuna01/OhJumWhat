package com.ohjumwhat.user;

import java.util.List;
import java.util.Locale;

import com.ohjumwhat.common.ApiException;

/**
 * 상세 프로필 입력 정리(Notion 「22. 프로필 항목 추가」): MBTI·퍼스널컬러·취미·나이·직급. 다섯 항목 모두 필수이고,
 * 오류 문구는 기획서 그대로다. 화면의 lib/profileDetails.ts와 같은 규칙이다.
 */
final class ProfileDetails {

	static final int HOBBY_MAX_LENGTH = 10;

	static final int HOBBIES_MAX = 5;

	static final int AGE_MIN = 1;

	static final int AGE_MAX = 120;

	static final int JOB_TITLE_MAX_LENGTH = 15;

	static final String MBTI_MESSAGE = "4가지 성향을 모두 선택해 주세요.";

	static final String PERSONAL_COLOR_MESSAGE = "퍼스널컬러를 선택해 주세요.";

	static final String HOBBIES_MESSAGE = "최소 1개 이상의 취미를 등록해 주세요.";

	static final String AGE_MESSAGE = "올바른 나이를 입력해 주세요. (1~120세)";

	static final String JOB_TITLE_MESSAGE = "직급을 입력해 주세요. (최대 15자)";

	private static final ProfileText.TagRule HOBBY_RULE = new ProfileText.TagRule(HOBBY_MAX_LENGTH, HOBBIES_MAX,
			"취미에 쓸 수 없는 문자가 있어요.", "취미는 " + HOBBY_MAX_LENGTH + "자 이하로 입력해 주세요.",
			"취미는 " + HOBBIES_MAX + "개까지 적을 수 있어요.");

	private ProfileDetails() {
	}

	/** E/I, S/N, T/F, J/P를 하나씩 고른 4글자(대소문자 상관없이 받아 대문자로 저장) */
	static String mbti(String raw) {
		String mbti = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
		if (!mbti.matches("[EI][SN][TF][JP]")) {
			throw ApiException.badRequest(MBTI_MESSAGE);
		}
		return mbti;
	}

	static PersonalColor personalColor(String raw) {
		if (raw == null || raw.isBlank()) {
			throw ApiException.badRequest(PERSONAL_COLOR_MESSAGE);
		}
		try {
			return PersonalColor.valueOf(raw.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest(PERSONAL_COLOR_MESSAGE);
		}
	}

	/** 취미(태그) 1~5개. 정리 규칙은 좋아하는 음식과 같다({@link ProfileText#tags}). */
	static List<String> hobbies(List<String> raw) {
		List<String> hobbies = ProfileText.tags(raw, HOBBY_RULE);
		if (hobbies.isEmpty()) {
			throw ApiException.badRequest(HOBBIES_MESSAGE);
		}
		return hobbies;
	}

	static short age(Integer raw) {
		if (raw == null || raw < AGE_MIN || raw > AGE_MAX) {
			throw ApiException.badRequest(AGE_MESSAGE);
		}
		return raw.shortValue();
	}

	/** 앞뒤·연속 공백을 정리한 1~15자 */
	static String jobTitle(String raw) {
		String jobTitle = ProfileText.singleLine(raw);
		if (jobTitle.isEmpty() || ProfileText.length(jobTitle) > JOB_TITLE_MAX_LENGTH) {
			throw ApiException.badRequest(JOB_TITLE_MESSAGE);
		}
		if (ProfileText.hasControl(jobTitle)) {
			throw ApiException.badRequest("직급에 쓸 수 없는 문자가 있어요.");
		}
		return jobTitle;
	}
}
