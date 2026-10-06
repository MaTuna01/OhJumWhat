package com.ohjumwhat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ohjumwhat.push.PushDispatcher;
import com.ohjumwhat.push.PushMessage;
import com.ohjumwhat.push.PushSender;

/**
 * 테스트에서 실제 FCM으로 보내지 않는다. 보낸 기록을 남기고, 지정한 FID는 더는 받을 수 없는 기기나 다른 오류로 답한다.
 * 푸시 작업은 바로 돌리지 않고 큐에 모아 두었다가 테스트가 drain()으로 돌린다(커밋 직후 같은 스레드에서 돌리면 끝난
 * 트랜잭션에 섞여 기기 삭제가 커밋되지 않는다).
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakePushSenderConfiguration {

	@Bean
	@Primary
	FakePushSender fakePushSender() {
		return new FakePushSender();
	}

	@Bean
	@Primary
	QueuedPushDispatcher queuedPushDispatcher() {
		return new QueuedPushDispatcher();
	}

	/** 보낸 기록 한 건 */
	public record Sent(List<String> fids, PushMessage message) {
	}

	public static class FakePushSender implements PushSender {

		private final List<Sent> sent = new ArrayList<>();

		private final Set<String> staleFids = new HashSet<>();

		private final Map<String, String> failedFids = new HashMap<>();

		private volatile boolean enabled = true;

		private volatile RuntimeException failure;

		private volatile Hook beforeSend;

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public synchronized Result send(List<String> fids, PushMessage message) {
			if (beforeSend != null) {
				try {
					beforeSend.run();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			if (failure != null) {
				throw failure;
			}
			sent.add(new Sent(List.copyOf(fids), message));
			List<String> stale = fids.stream().filter(staleFids::contains).toList();
			List<String> errorCodes = fids.stream().map(failedFids::get).filter(code -> code != null).distinct().toList();
			int failed = (int) fids.stream().filter(failedFids::containsKey).count();
			return new Result(fids.size() - stale.size() - failed, stale, errorCodes);
		}

		public synchronized List<Sent> sent() {
			return List.copyOf(sent);
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		/** 이 FID로 보내면 더는 받을 수 없는 기기로 답한다. */
		public synchronized void markStale(String fid) {
			staleFids.add(fid);
		}

		/** 이 FID로 보내면 이 오류 코드로 실패한다(지우지 않을 실패). */
		public synchronized void markFailed(String fid, String errorCode) {
			failedFids.put(fid, errorCode);
		}

		/** 보낼 때마다 이 예외를 낸다. */
		public void failWith(RuntimeException failure) {
			this.failure = failure;
		}

		/** FCM에 보내는 사이에 일어나는 일(보내기 전에 부른다). */
		public void beforeSend(Hook hook) {
			this.beforeSend = hook;
		}

		public synchronized void reset() {
			sent.clear();
			staleFids.clear();
			failedFids.clear();
			enabled = true;
			failure = null;
			beforeSend = null;
		}

		public interface Hook {

			void run() throws Exception;
		}
	}

	/** 맡긴 작업을 모아 두었다가 drain()이 부른 스레드에서 차례로 돌린다. */
	public static class QueuedPushDispatcher extends PushDispatcher {

		private final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public synchronized void dispatch(Runnable task) {
			tasks.add(task);
		}

		/** 모인 작업을 모두 돌린다(돌리는 중에 새로 맡긴 작업까지). */
		public void drain() {
			Runnable task;
			while ((task = poll()) != null) {
				task.run();
			}
		}

		public synchronized int size() {
			return tasks.size();
		}

		public synchronized void clear() {
			tasks.clear();
		}

		private synchronized Runnable poll() {
			return tasks.poll();
		}
	}
}
