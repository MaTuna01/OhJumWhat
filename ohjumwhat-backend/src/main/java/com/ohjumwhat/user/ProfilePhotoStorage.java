package com.ohjumwhat.user;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 프로필 사진 파일 저장소. 사진은 {폴더}/{key}.jpg 하나이고, key는 새로 올릴 때마다 새로 만든다(UUID hex 32자).
 * DB(users.photo_key)가 파일을 가리키므로, 새 파일은 DB를 바꾸기 전에 쓰고 옛 파일은 커밋한 뒤에 지운다.
 * 그래서 DB가 없는 파일을 가리키는 일은 없고, 중간에 실패하면 아무도 가리키지 않는 파일만 남을 수 있다(작아서 그대로 둔다).
 */
@Slf4j
@Component
public class ProfilePhotoStorage {

	private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");

	private static final String EXTENSION = ".jpg";

	private final Path dir;

	public ProfilePhotoStorage(ProfilePhotoProperties properties) {
		this.dir = Path.of(properties.dir()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(dir);
			log.info("프로필 사진 폴더: {}", dir);
		}
		catch (IOException e) {
			// 서버 시작은 막지 않는다(시작이 실패하면 배포 헬스 체크가 실패해 서비스가 멈춘다). 사진 올리기만 실패한다.
			log.error("프로필 사진 폴더를 만들지 못했습니다: {}", dir, e);
		}
	}

	/** JPEG 바이트를 새 파일로 쓰고 키를 돌려준다. 다 쓴 뒤에 이름을 바꿔서, 반쯤 쓴 파일이 사진으로 보이지 않게 한다. */
	public String write(byte[] jpeg) {
		String key = UUID.randomUUID().toString().replace("-", "");
		try {
			Path temp = Files.createTempFile(dir, key, ".tmp");
			try {
				Files.write(temp, jpeg);
				Files.move(temp, dir.resolve(key + EXTENSION), StandardCopyOption.ATOMIC_MOVE);
			}
			finally {
				Files.deleteIfExists(temp);
			}
			return key;
		}
		catch (IOException e) {
			throw new UncheckedIOException("프로필 사진을 저장하지 못했습니다.", e);
		}
	}

	/** 키 형식이 맞고 파일이 있으면 그 경로. 사용자가 보낸 키로 폴더 밖을 읽지 못하게 형식을 먼저 확인한다. */
	public Optional<Path> find(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			return Optional.empty();
		}
		Path file = dir.resolve(key + EXTENSION);
		return file.startsWith(dir) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
	}

	/** 파일을 지운다. 실패해도 예외를 내지 않는다(아무도 가리키지 않는 파일이 남을 뿐이다). */
	public void delete(String key) {
		find(key).ifPresent(file -> {
			try {
				Files.deleteIfExists(file);
			}
			catch (IOException e) {
				log.warn("프로필 사진 파일을 지우지 못했습니다: {}", file.getFileName(), e);
			}
		});
	}

	/** 지금 트랜잭션이 커밋되면 지운다(롤백되면 그대로 둔다). 트랜잭션 밖이면 바로 지운다. */
	public void deleteAfterCommit(String key) {
		if (key == null) {
			return;
		}
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

	public Path dir() {
		return dir;
	}
}
