package com.ohjumwhat.poll;

import io.swagger.v3.oas.annotations.media.Schema;

/** 투표에 대한 내 응답: 아직 안 함 / 오늘은 패스 / 메뉴에 참여 */
@Schema(description = "내 응답: NONE 아직 안 함, PASS 오늘은 패스, OPTION 메뉴에 참여")
public enum MyResponse {
	NONE, PASS, OPTION
}
