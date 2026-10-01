package com.ohjumwhat.menu;

import java.time.LocalDate;

/**
 * 메뉴 자동완성 항목.
 *
 * @param lastEatenOn 마지막으로 먹은 날(마감된 투표에서 참여자가 있었던 날). 먹은 적이 없으면 null
 */
public record MenuSuggestion(String name, LocalDate lastEatenOn) {
}
