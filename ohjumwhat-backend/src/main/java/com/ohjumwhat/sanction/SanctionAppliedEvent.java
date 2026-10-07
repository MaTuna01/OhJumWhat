package com.ohjumwhat.sanction;

/** 관리자가 제재를 걸었다(본인에게 알릴 거리). 사유·설명은 싣지 않는다. */
public record SanctionAppliedEvent(Long sanctionId, Long userId) {
}
