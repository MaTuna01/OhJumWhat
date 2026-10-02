package com.ohjumwhat.chat;

/** 메시지를 쓰고 고치고 지운 뒤(커밋 후) 같은 투표의 WebSocket 연결로 보낸다. */
record ChatEvent(Long pollId, ChatPush push) {

	/** WebSocket으로 보내는 글: {"type": "created" | "updated" | "deleted", "message": {...}} */
	record ChatPush(String type, ChatMessageResponse message) {
	}

	static ChatEvent created(Long pollId, ChatMessageResponse message) {
		return new ChatEvent(pollId, new ChatPush("created", message));
	}

	static ChatEvent updated(Long pollId, ChatMessageResponse message) {
		return new ChatEvent(pollId, new ChatPush("updated", message));
	}

	static ChatEvent deleted(Long pollId, ChatMessageResponse message) {
		return new ChatEvent(pollId, new ChatPush("deleted", message));
	}
}
