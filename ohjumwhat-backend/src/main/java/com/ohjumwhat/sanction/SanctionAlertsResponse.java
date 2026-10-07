package com.ohjumwhat.sanction;

import java.util.List;

/** 아직 보지 않은 제재 안내(오래된 순, 최대 20건) */
public record SanctionAlertsResponse(List<SanctionNotice> sanctions) {
}
