package com.ohjumwhat.push;

import java.util.List;

/** 푸시 설정이 없거나 초기화하지 못했을 때. 아무것도 보내지 않는다. */
class DisabledPushSender implements PushSender {

	@Override
	public boolean enabled() {
		return false;
	}

	@Override
	public Result send(List<String> fids, PushMessage message) {
		return new Result(0, List.of());
	}
}
