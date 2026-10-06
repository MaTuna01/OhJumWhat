package com.ohjumwhat.push;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * 푸시 보내기를 요청 스레드 밖에서 돌린다. 앱 전체의 비동기 설정(@EnableAsync·Executor 빈)을 만들면 Spring Boot의 기본
 * Executor 빈이 사라지므로, 푸시 전용 스레드 풀을 여기에 숨겨 든다. 2스레드, 대기 500건이고 넘치면 버린다(알림은 놓쳐도
 * 화면의 배지로 다시 알 수 있다). 서버가 멈출 때 끝낸다.
 */
@Slf4j
@Component
public class PushDispatcher implements DisposableBean {

	static final int THREADS = 2;

	static final int QUEUE_CAPACITY = 500;

	private static final long SHUTDOWN_WAIT_SECONDS = 5;

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(QUEUE_CAPACITY), threadFactory(),
			(task, pool) -> log.warn("푸시 대기열이 가득 차 푸시 하나를 버립니다."));

	/** 작업을 맡기고 바로 돌아온다. 작업 안의 예외는 작업이 잡는다. */
	public void dispatch(Runnable task) {
		executor.execute(task);
	}

	@Override
	public void destroy() throws InterruptedException {
		executor.shutdown();
		if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
			executor.shutdownNow();
		}
	}

	private static ThreadFactory threadFactory() {
		AtomicInteger count = new AtomicInteger();
		return task -> {
			Thread thread = new Thread(task, "push-" + count.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
