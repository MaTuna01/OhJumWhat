package com.ohjumwhat.menu;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 메뉴 자동완성 항목.
 *
 * @param lastEatenOn 마지막으로 먹은 날(마감된 투표에서 참여자가 있었던 날). 먹은 적이 없으면 null
 * @param lastPlace 같은 이름의 메뉴에 지난번 붙인 식당. 없으면 null
 */
@Schema(description = "메뉴 자동완성 항목")
public record MenuSuggestion(
		@Schema(description = "메뉴 이름", example = "김치찌개") String name,
		@Schema(description = "마지막으로 먹은 날(한국 날짜). 먹은 적이 없으면 null", example = "2026-10-08", nullable = true)
		LocalDate lastEatenOn,
		@Schema(description = "같은 이름의 메뉴에 지난번 붙인 식당. 없으면 null", nullable = true) LastPlace lastPlace) {
}
