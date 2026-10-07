package com.ohjumwhat.push;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.ohjumwhat.guestbook.GuestbookEntryCreatedEvent;
import com.ohjumwhat.guestbook.GuestbookEntryRestrictedEvent;
import com.ohjumwhat.guestbook.GuestbookEntryRepository;
import com.ohjumwhat.letter.LetterDeliveredEvent;
import com.ohjumwhat.letter.LetterRepository;
import com.ohjumwhat.sanction.SanctionAppliedEvent;
import com.ohjumwhat.sanction.UserSanctionRepository;

/**
 * 커밋된 일을 받는 사람의 기기로 알린다. 요청 스레드에서는 커밋 뒤에 작업을 맡기기만 하고 바로 돌아온다(PushDispatcher).
 * 작업 스레드에서 ① 받을 사람의 기기를 읽고(없으면 끝, 묶인 로그인이 다른 사람에게 넘어간 기기는 지운다) ② 커밋된 상태에서
 * 문구용 데이터를 다시 읽고(읽기 전용 트랜잭션) ③ 트랜잭션 밖에서 보내고 ④ 더는 받을 수 없는 기기를 지운다.
 * 예외는 모두 잡아 경고 로그만 남긴다(알림이 실패해도 원래 요청은 성공이다).
 *
 * <p>로그에는 종류·보낸 수·받을 수 없는 기기 수·오류 코드만 남긴다. 이름·본문·FID·회원·쪽지 ID는 남기지 않는다. 쪽지는 보낸
 * 결과도 debug로만 남긴다(익명 쪽지를 받은 사람이 로그의 시각·기기 수로 짐작되지 않게).
 */
@Slf4j
@Component
class PushNotifier {

	private final PushSender pushSender;

	private final PushDispatcher dispatcher;

	private final PushDeviceRepository deviceRepository;

	private final GuestbookEntryRepository guestbookEntryRepository;

	private final LetterRepository letterRepository;

	private final UserSanctionRepository sanctionRepository;

	private final TransactionTemplate readOnly;

	private final Clock clock;

	PushNotifier(PushSender pushSender, PushDispatcher dispatcher, PushDeviceRepository deviceRepository,
			GuestbookEntryRepository guestbookEntryRepository, LetterRepository letterRepository,
			UserSanctionRepository sanctionRepository, PlatformTransactionManager transactionManager, Clock clock) {
		this.pushSender = pushSender;
		this.dispatcher = dispatcher;
		this.deviceRepository = deviceRepository;
		this.guestbookEntryRepository = guestbookEntryRepository;
		this.letterRepository = letterRepository;
		this.sanctionRepository = sanctionRepository;
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
		this.clock = clock;
	}

	/** 방명록 주인에게. 그사이 지워졌거나 제한된 글이면 보내지 않는다. */
	@TransactionalEventListener
	void onGuestbookEntryCreated(GuestbookEntryCreatedEvent event) {
		notify(PushKind.GUESTBOOK, event.ownerId(),
				() -> guestbookEntryRepository.findAuthorNameOfShown(event.entryId()).map(PushMessages::guestbook));
	}

	/** 제한된 글을 쓴 사람에게(앱을 열면 경고 안내 창이 뜬다) */
	@TransactionalEventListener
	void onGuestbookEntryRestricted(GuestbookEntryRestrictedEvent event) {
		notify(PushKind.GUESTBOOK_RESTRICTED, event.authorId(), () -> Optional.of(PushMessages.guestbookRestricted()));
	}

	/**
	 * 쪽지를 받은 사람에게. 받은 쪽지함과 같은 쿼리로 읽어 같은 이름을 쓴다(익명 쪽지는 이름을 고르지 않는다). 그사이 지웠거나
	 * 차단했으면 보내지 않는다.
	 */
	@TransactionalEventListener
	void onLetterDelivered(LetterDeliveredEvent event) {
		notify(PushKind.LETTER, event.recipientId(),
				() -> letterRepository.findReceivedRow(event.letterId(), event.recipientId())
					.flatMap(row -> PushMessages.letter(row.anonymous(), row.replyToId() != null,
							row.counterpartName())));
	}

	/** 제재 받은 회원에게(앱을 열면 제재 안내 창이 뜬다). 그사이 제재가 없어졌으면(강제 탈퇴) 보내지 않는다. */
	@TransactionalEventListener
	void onSanctionApplied(SanctionAppliedEvent event) {
		notify(PushKind.SANCTION, event.userId(), () -> sanctionRepository.findById(event.sanctionId())
			.map(sanction -> PushMessages.sanction(!sanction.getRestrictions().isEmpty(),
					!sanction.getResets().isEmpty())));
	}

	private void notify(PushKind kind, Long userId, Supplier<Optional<PushMessage>> message) {
		if (userId == null || !pushSender.enabled()) {
			return;
		}
		dispatcher.dispatch(() -> deliver(kind, userId, message));
	}

	private void deliver(PushKind kind, Long userId, Supplier<Optional<PushMessage>> message) {
		try {
			Instant readAt = Instant.now(clock);
			deviceRepository.deleteTakenOver(userId);
			List<String> fids = deviceRepository.findFids(userId);
			if (fids.isEmpty()) {
				return;
			}
			Optional<PushMessage> push = readOnly.execute(status -> message.get());
			if (push == null || push.isEmpty()) {
				return;
			}
			PushSender.Result result = pushSender.send(fids, push.get());
			if (!result.staleFids().isEmpty()) {
				deviceRepository.deleteStale(result.staleFids(), readAt);
			}
			if (!result.errorCodes().isEmpty()) {
				log.warn("푸시를 일부 기기에 보내지 못했어요: kind={}, 오류={}", kind, result.errorCodes());
			}
			if (kind == PushKind.LETTER) {
				log.debug("푸시 보내기: kind={}, 보낸 수={}, 받을 수 없는 기기 수={}", kind, result.sent(),
						result.staleFids().size());
			}
			else {
				log.info("푸시 보내기: kind={}, 보낸 수={}, 받을 수 없는 기기 수={}", kind, result.sent(),
						result.staleFids().size());
			}
		}
		catch (Exception e) {
			// 오류 메시지에 FID·이름이 섞일 수 있어 종류만 남긴다(SendFailedException은 오류 코드만 담는다).
			String error = e instanceof PushSender.SendFailedException ? e.getMessage() : e.getClass().getSimpleName();
			log.warn("푸시를 보내지 못했어요: kind={}, 오류={}", kind, error);
		}
	}
}
