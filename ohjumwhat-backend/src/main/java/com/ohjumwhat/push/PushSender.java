package com.ohjumwhat.push;

import java.util.List;

/** 기기로 푸시를 보낸다. 설정이 없거나 초기화하지 못했으면 꺼져 있다(DisabledPushSender). */
public interface PushSender {

	/** 푸시를 쓸 수 있는지(기기 등록과 /api/config의 push도 이것을 따른다) */
	boolean enabled();

	/**
	 * 같은 내용을 여러 기기로 보낸다. 네트워크를 쓰므로 트랜잭션 밖에서 부른다.
	 *
	 * @param fids 받을 기기의 Firebase 설치 ID(한 사람의 기기, 많아야 10대)
	 * @throws SendFailedException 요청 전체가 실패했다(기기마다의 실패는 Result에 담는다)
	 */
	Result send(List<String> fids, PushMessage message);

	/**
	 * @param sent 보낸 기기 수
	 * @param staleFids 더는 받을 수 없는 기기(구독 해제·다른 프로젝트). 호출한 쪽이 지운다
	 * @param errorCodes 그 밖의 이유로 보내지 못한 기기들의 오류 코드(중복 없이, FID 없이). 운영에서 원인을 보려고 로그에 남긴다
	 */
	record Result(int sent, List<String> staleFids, List<String> errorCodes) {
	}

	/** 요청 전체가 실패했다. 메시지에는 오류 코드만 담는다(FID·이름 없이, 그대로 로그에 남긴다). */
	class SendFailedException extends RuntimeException {

		public SendFailedException(String errorCode) {
			super(errorCode);
		}
	}
}
