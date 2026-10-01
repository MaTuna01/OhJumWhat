package com.ohjumwhat.user;

/** 강제 탈퇴로 차단된 구글 계정이 로그인하려고 할 때. 로그인 처리에서 OAuth2 인증 실패로 바꾼다. */
public class BlockedAccountException extends RuntimeException {

	private final Long blockId;

	public BlockedAccountException(Long blockId) {
		super("차단된 계정이에요.");
		this.blockId = blockId;
	}

	public Long getBlockId() {
		return blockId;
	}
}
