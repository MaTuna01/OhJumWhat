package com.ohjumwhat.push;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;

/**
 * 이 기기에서 알림 받기 켜기·끄기. 기기는 그 기기의 로그인(세션)에 묶이고, 한 사람당 최근 10대까지만 남긴다.
 * 로그에는 FID를 남기지 않는다.
 */
@Service
public class PushDeviceService {

	static final int MAX_DEVICES_PER_USER = 10;

	/** Firebase 설치 ID(FID) 모양. 실제로는 22자지만 앞으로 바뀌어도 받도록 넉넉히 둔다. */
	private static final Pattern FID = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

	private final PushDeviceRepository deviceRepository;

	private final PushSender pushSender;

	private final Clock clock;

	public PushDeviceService(PushDeviceRepository deviceRepository, PushSender pushSender, Clock clock) {
		this.deviceRepository = deviceRepository;
		this.pushSender = pushSender;
		this.clock = clock;
	}

	/**
	 * 이 로그인(세션)의 기기로 등록한다. 같은 브라우저에서 다른 계정이 켜면 그 사람에게 옮겨 간다.
	 *
	 * @param sessionId 지금 요청의 HTTP 세션 ID(없으면 null)
	 * @return 새로 등록했으면 true
	 */
	@Transactional
	public boolean register(Long userId, String sessionId, String fid) {
		if (fid == null || !FID.matcher(fid).matches()) {
			throw ApiException.badRequest("알림을 받을 기기 정보가 올바르지 않아요.");
		}
		if (!pushSender.enabled()) {
			throw ApiException.conflict("지금은 알림을 켤 수 없어요.");
		}
		if (sessionId == null) {
			throw ApiException.unauthorized("다시 로그인해 주세요.");
		}
		boolean created = deviceRepository.upsert(userId, fid, sessionId, Instant.now(clock))
			.orElseThrow(() -> ApiException.unauthorized("다시 로그인해 주세요."));
		deviceRepository.keepRecent(userId, MAX_DEVICES_PER_USER);
		return created;
	}

	/** 이 기기에서 알림 끄기. 내 기기만 지우고, 없으면 그대로 둔다. */
	@Transactional
	public void unregister(Long userId, String fid) {
		deviceRepository.delete(userId, fid);
	}
}
