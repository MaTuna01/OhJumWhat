package com.ohjumwhat.letter;

import java.util.List;

/** 쪽지함 한 쪽(최신순 20통). hasMore면 마지막 쪽지의 id를 before로 다음 쪽을 받는다. */
public record LetterPageResponse(List<LetterResponse> letters, boolean hasMore) {
}
