package com.ohjumwhat.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.ohjumwhat.chat.ChatPhotoStorage.StoredFile;

/**
 * 채팅 사진 정리(매일 04:00 KST, {@link ChatPhotoJanitorScheduler}). 폴더를 훑어 DB가 더는 보여주지 않는 사진의 파일을 지운다.
 * 보관 기간(30일)이 지난 사진, 지운 메시지의 사진, 투표·조직을 지워 CASCADE로 없어진 메시지의 사진, 저장에 실패해 아무도 가리키지 않는
 * 파일이 모두 여기 해당한다. 방금 쓰고 아직 메시지를 저장하지 않은 파일을 지우지 않게, 만든 지 1시간이 안 된 파일은 두고 다음 날 본다.
 */
@Slf4j
@Component
class ChatPhotoJanitor {

	/** 이보다 최근에 쓴 파일은 건드리지 않는다(쓰는 중이거나 메시지를 저장하는 중일 수 있다). */
	static final Duration GRACE = Duration.ofHours(1);

	private static final int BATCH = 500;

	private final ChatPhotoStorage storage;

	private final ChatMessageRepository chatMessageRepository;

	private final Clock clock;

	ChatPhotoJanitor(ChatPhotoStorage storage, ChatMessageRepository chatMessageRepository, Clock clock) {
		this.storage = storage;
		this.chatMessageRepository = chatMessageRepository;
		this.clock = clock;
	}

	/** 지운 파일 수 */
	int clean() {
		Instant now = Instant.now(clock);
		Instant oldEnough = now.minus(GRACE);
		List<StoredFile> files = storage.list().stream().filter(file -> file.modifiedAt().isBefore(oldEnough)).toList();
		Set<String> keys = new HashSet<>();
		files.stream().map(StoredFile::key).filter(key -> key != null).forEach(keys::add);
		Set<String> live = liveKeys(keys, now.minus(ChatMessage.PHOTO_RETENTION));
		int deleted = 0;
		for (StoredFile file : files) {
			if (file.key() != null && live.contains(file.key())) {
				continue;
			}
			try {
				if (Files.deleteIfExists(file.path())) {
					deleted++;
				}
			}
			catch (IOException e) {
				log.warn("채팅 사진 파일을 지우지 못했습니다: {}", file.path().getFileName(), e);
			}
		}
		log.info("채팅 사진 정리: files={}, deleted={}", files.size(), deleted);
		return deleted;
	}

	private Set<String> liveKeys(Set<String> keys, Instant cutoff) {
		Set<String> live = new HashSet<>();
		List<String> all = List.copyOf(keys);
		for (int from = 0; from < all.size(); from += BATCH) {
			live.addAll(chatMessageRepository.findLiveImageKeys(all.subList(from, Math.min(from + BATCH, all.size())),
					cutoff));
		}
		return live;
	}
}
