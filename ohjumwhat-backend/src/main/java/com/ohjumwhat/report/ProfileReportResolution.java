package com.ohjumwhat.report;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자가 사람 신고를 처리한 결과. 이름은 API·DB(profile_reports.resolution)에 그대로 쓴다. */
@Schema(description = "사람 신고 처리 결과. DISMISSED = 문제 없음, ACTIONED = 제재를 걸었다(경고 포함, 결과 사본이 함께 있다), "
		+ "WITHDRAWN = 강제 탈퇴시켰다", example = "ACTIONED")
public enum ProfileReportResolution {

	/** 문제 없음: 조치하지 않았다. */
	DISMISSED,

	/** 신고된 사람에게 제재를 걸었다(경고 포함). 결과 사본이 함께 있다. */
	ACTIONED,

	/** 신고된 사람을 강제 탈퇴시켰다. */
	WITHDRAWN
}
