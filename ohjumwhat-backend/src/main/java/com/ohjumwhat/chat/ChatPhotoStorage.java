package com.ohjumwhat.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 채팅 사진 파일 저장소. 사진 하나는 {폴더}/{key}.jpg(원본)와 {key}_t.jpg(썸네일) 두 파일이고, key는 UUID hex 32자다.
 * DB(chat_messages.image_key)가 파일을 가리키므로 파일은 DB에 저장하기 전에 쓰고, 지운 메시지의 파일은 커밋한 뒤에 지운다.
 * 아무도 가리키지 않게 된 파일(투표·조직 삭제, 실패한 저장, 보관 기간 지남)은 매일 {@link ChatPhotoJanitor}가 지운다.
 */
@Slf4j
@Component
public class ChatPhotoStorage {

	private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");

	private static final Pattern FILE = Pattern.compile("([0-9a-f]{32})(_t)?\\.jpg");

	private static final String THUMBNAIL_SUFFIX = "_t";

	private final Path dir;

	public ChatPhotoStorage(ChatPhotoProperties properties) {
		this.dir = Path.of(properties.dir()).toAbsolutePath().normalize();
		log.info("채팅 사진 폴더: {}", dir);
	}

	/** 원본·썸네일을 새 키로 쓰고 키를 돌려준다. 다 쓴 뒤에 이름을 바꿔서, 반쯤 쓴 파일이 사진으로 보이지 않게 한다. */
	String write(ChatPhotoImages.Processed photo) {
		String key = UUID.randomUUID().toString().replace("-", "");
		try {
			// 테스트가 폴더를 통째로 비우거나 운영에서 폴더가 지워져도 다시 만든다.
			Files.createDirectories(dir);
			writeFile(key + THUMBNAIL_SUFFIX, photo.thumbnail());
			writeFile(key, photo.full());
			return key;
		}
		catch (IOException e) {
			delete(key);
			throw new UncheckedIOException("채팅 사진을 저장하지 못했습니다.", e);
		}
	}

	private void writeFile(String name, byte[] jpeg) throws IOException {
		Path temp = Files.createTempFile(dir, name, ".tmp");
		try {
			Files.write(temp, jpeg);
			Files.move(temp, dir.resolve(name + ".jpg"), StandardCopyOption.ATOMIC_MOVE);
		}
		finally {
			Files.deleteIfExists(temp);
		}
	}

	/** 키 형식이 맞고 파일이 있으면 그 경로. 사용자가 보낸 키로 폴더 밖을 읽지 못하게 형식을 먼저 확인한다. */
	Optional<Path> find(String key, boolean thumbnail) {
		if (key == null || !KEY.matcher(key).matches()) {
			return Optional.empty();
		}
		Path file = dir.resolve(key + (thumbnail ? THUMBNAIL_SUFFIX : "") + ".jpg");
		return file.startsWith(dir) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
	}

	/** 원본·썸네일을 지운다. 실패해도 예외를 내지 않는다(정리 작업이 다시 지운다). */
	void delete(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			return;
		}
		for (String name : List.of(key + ".jpg", key + THUMBNAIL_SUFFIX + ".jpg")) {
			try {
				Files.deleteIfExists(dir.resolve(name));
			}
			catch (IOException e) {
				log.warn("채팅 사진 파일을 지우지 못했습니다: {}", name, e);
			}
		}
	}

	/** 지금 트랜잭션이 커밋되면 지운다(롤백되면 그대로 둔다). 트랜잭션 밖이면 바로 지운다. */
	void deleteAfterCommit(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			delete(key);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				delete(key);
			}
		});
	}

	/** 폴더의 사진 파일(키, 마지막으로 바뀐 시각). 쓰다 남은 임시 파일(.tmp)은 키가 없어 이름 그대로 준다. */
	List<StoredFile> list() {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		List<StoredFile> files = new ArrayList<>();
		try (Stream<Path> paths = Files.list(dir)) {
			for (Path path : paths.toList()) {
				if (!Files.isRegularFile(path)) {
					continue;
				}
				Matcher matcher = FILE.matcher(path.getFileName().toString());
				try {
					files.add(new StoredFile(path, matcher.matches() ? matcher.group(1) : null,
							Files.getLastModifiedTime(path).toInstant()));
				}
				catch (IOException e) {
					log.warn("채팅 사진 파일을 읽지 못했습니다: {}", path.getFileName(), e);
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException("채팅 사진 폴더를 읽지 못했습니다.", e);
		}
		return files;
	}

	/**
	 * 폴더의 파일 하나.
	 *
	 * @param key 사진 키(사진 파일이 아니면 null)
	 */
	record StoredFile(Path path, String key, Instant modifiedAt) {
	}

	public Path dir() {
		return dir;
	}
}
