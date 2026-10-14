package com.ohjumwhat.poll;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OPEN 진행 중, CLOSED 마감(요청 시각이 closesAt 이후)")
public enum PollStatus {
	OPEN, CLOSED
}
