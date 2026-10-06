package com.ohjumwhat.push;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 푸시 보내기를 요청 스레드 밖에서 돌린다. 앱 전체의 비동기 설정(@EnableAsync·Executor 빈)을 만들면 Spring Boot의 기본
 * Executor 빈이 사라지므로, 푸시 전용 스레드 풀을 여기에 숨겨 든다. 2스레드, 대기 500건이고 넘치면 버린다(알림은 놓쳐도
 * 화면의 배지로 다시 알 수 있다).
 *
 * <p>서버가 멈출 때는 웹 서버가 요청을 다 끝낸 뒤(그 요청들이 맡긴 푸시까지 받은 뒤), 빈을 정리하기 전에 남은 작업을 끝낸다
 * (SmartLifecycle). 그래서 작업이 쓰는 DB·JPA·Firebase가 아직 살아 있다.
 */
@Slf4j
@Component
public class PushDispatcher implements SmartLifecycle, DisposableBean {

	static final int THREADS = 2;

	static final int QUEUE_CAPACITY = 500;

	/** 웹 서버가 멈춘 다음에 멈춘다(값이 작을수록 나중에 멈춘다). */
	static final int PHASE = WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE - 1;

	private static final long SHUTDOWN_WAIT_SECONDS = 5;

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(QUEUE_CAPACITY), threadFactory(),
			(task, pool) -> log.warn(pool.isShutdown() ? "서버가 멈추는 중이라 푸시 하나를 버립니다."
					: "푸시 대기열이 가득 차 푸시 하나를 버립니다."));

	private volatile boolean running;

	/** 작업을 맡기고 바로 돌아온다. 작업 안의 예외는 작업이 잡는다. */
	public void dispatch(Runnable task) {
		executor.execute(task);
	}

	@Override
	public void start() {
		running = true;
	}

	/** 새 작업을 받지 않고, 남은 작업을 잠깐 기다린 뒤 끝낸다. */
	@Override
	public void stop() {
		running = false;
		executor.shutdown();
		try {
			if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		}
		catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getPhase() {
		return PHASE;
	}

	/** 시작하지 못하고 닫히는 경우(stop이 불리지 않는다)에도 스레드를 남기지 않는다. */
	@Override
	public void destroy() {
		executor.shutdownNow();
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
