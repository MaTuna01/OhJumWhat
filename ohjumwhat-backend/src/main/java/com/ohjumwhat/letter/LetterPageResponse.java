package com.ohjumwhat.letter;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 쪽지함 한 쪽(최신순 20통). hasMore면 마지막 쪽지의 id를 before로 다음 쪽을 받는다. */
@Schema(description = "쪽지함 한 쪽(최신순 20통)")
public record LetterPageResponse(@Schema(description = "쪽지(최신순)") List<LetterResponse> letters,
		@Schema(description = "다음 쪽이 있는지. 있으면 마지막 쪽지의 id를 before로 보낸다", example = "false") boolean hasMore) {
}
