package com.ohjumwhat;

import static org.assertj.core.api.Assertions.fail;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 두 트랜잭션의 순서를 강제하는 동시성 테스트의 공통 도구.
 *
 * <p>{@code @Transactional} 서비스 안에는 훅을 꽂을 수 없으므로, 보조 트랜잭션이 DB 행 잠금({@code select … for update}나
 * 커밋하지 않은 INSERT)을 쥔 채 기다리게 해서 실제 서비스 코드가 읽기는 다 마치고 쓰기(UPDATE·INSERT·FK 검사)에서 멈추게 한다.
 * 그사이 상대 트랜잭션을 커밋하고 잠금을 풀면 멈춰 있던 쪽이 이어서 커밋한다. 같은 행을 기다리는 트랜잭션이 둘이면
 * PostgreSQL은 도착한 순서대로 잠금을 주므로 순서가 결정된다.
 *
 * <p>주의: 테스트 메서드에 {@code @Transactional}을 붙이지 않는다(메인 스레드가 트랜잭션을 쥐면 다른 스레드의 커밋이 보이지 않는다).
 * {@link TestClock}은 모든 스레드가 공유하므로 워커 스레드에서 움직이지 않는다. 동시에 쓰는 트랜잭션은 넷을 넘기지 않는다(풀 10).
 * 잠금은 {@link #releaseAll}이 반드시 풀어야 한다. 안 풀면 뒤따르는 TRUNCATE가 영원히 기다린다.
 */
public abstract class ConcurrencyTest extends IntegrationTest {

	private static final long TIMEOUT_SECONDS = 15;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private DataSource dataSource;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<RowLock> locks = new ArrayList<>();

	/** 보조 트랜잭션이 쥔 잠금. {@link #release}로 풀면(커밋 또는 롤백) 그 트랜잭션이 끝난다. */
	public final class RowLock {

		private final CountDownLatch locked = new CountDownLatch(1);

		private final CountDownLatch release = new CountDownLatch(1);

		private final CountDownLatch done = new CountDownLatch(1);

		private volatile Throwable error;

		public void release() {
			release.countDown();
			try {
				if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					fail("잠금을 쥔 트랜잭션이 끝나지 않았습니다.");
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			if (error != null) {
				throw new IllegalStateException("잠금을 쥔 트랜잭션이 실패했습니다.", error);
			}
		}
	}

	/** 조회 문장(select … for update)으로 행을 잠그고, 풀 때 롤백한다. */
	protected RowLock holdLock(String selectForUpdate, Object... args) {
		return hold(() -> jdbcTemplate.queryForList(selectForUpdate, args), false);
	}

	/** INSERT(또는 UPDATE)를 커밋하지 않은 채 들고 있는다. 풀 때 커밋할지 롤백할지 고른다. */
	protected RowLock holdUncommitted(String statement, boolean commitOnRelease, Object... args) {
		return hold(() -> jdbcTemplate.update(statement, args), commitOnRelease);
	}

	private RowLock hold(Runnable statement, boolean commitOnRelease) {
		RowLock lock = new RowLock();
		executor.submit(() -> {
			try {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					statement.run();
					lock.locked.countDown();
					try {
						if (!lock.release.await(60, TimeUnit.SECONDS)) {
							throw new IllegalStateException("잠금을 60초 안에 풀지 않았습니다.");
						}
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					if (!commitOnRelease) {
						status.setRollbackOnly();
					}
				});
			}
			catch (Throwable t) {
				lock.error = t;
				lock.locked.countDown();
			}
			finally {
				lock.done.countDown();
			}
		});
		try {
			if (!lock.locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				fail("잠금을 잡지 못했습니다.");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		if (lock.error != null) {
			throw new IllegalStateException("잠금 문장이 실패했습니다.", lock.error);
		}
		locks.add(lock);
		return lock;
	}

	/** 별도 스레드에서 실행한다(서비스는 userId를 인자로 받으므로 SecurityContext가 필요 없다). */
	protected <T> Future<T> inThread(Callable<T> task) {
		return executor.submit(task);
	}

	protected Future<Void> inThread(Runnable task) {
		return executor.submit(() -> {
			task.run();
			return null;
		});
	}

	/**
	 * 별도 스레드의 트랜잭션 안에서 실행하고, 끝난 뒤 {@code commitWhen}이 열릴 때까지 커밋을 미룬다. IDENTITY 키라 INSERT는
	 * save 때 바로 나가므로 「문장은 실행됐지만 커밋은 안 된」 상태(FK 검사의 KEY SHARE를 쥔 채)를 만들 수 있다.
	 */
	protected <T> Future<T> inTransactionThread(Callable<T> work, CountDownLatch commitWhen) {
		return executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
			try {
				T result = work.call();
				if (!commitWhen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					throw new IllegalStateException("커밋 신호가 오지 않았습니다.");
				}
				return result;
			}
			catch (RuntimeException e) {
				throw e;
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}));
	}

	/** 다른 연결이 그 문장을 실행한 뒤 커밋하지 않은 채(idle in transaction) 있을 때까지 기다린다. */
	protected void awaitIdleInTransaction(String queryLike) {
		awaitTrue("idle in transaction: " + queryLike, () -> {
			Long count = jdbcTemplate.queryForObject("""
					select count(*) from pg_stat_activity
					where datname = current_database() and state = 'idle in transaction' and query ilike ?""", Long.class,
					queryLike);
			return count != null && count >= 1;
		});
	}

	/** 결과를 기다린다. 작업이 예외로 끝났으면 그 예외(원인)를 다시 던진다. */
	protected <T> T await(Future<T> future) throws Exception {
		try {
			return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (ExecutionException e) {
			if (e.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw e;
		}
		catch (TimeoutException e) {
			future.cancel(true);
			throw new AssertionError("작업이 " + TIMEOUT_SECONDS + "초 안에 끝나지 않았습니다(잠금에 막혀 있을 수 있습니다).", e);
		}
	}

	/** 작업이 예외로 끝나기를 기대하고 그 예외를 돌려준다. */
	protected Throwable awaitFailure(Future<?> future) {
		try {
			Object result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			throw new AssertionError("예외가 나야 하는데 끝났습니다: " + result);
		}
		catch (ExecutionException e) {
			return e.getCause();
		}
		catch (InterruptedException | TimeoutException e) {
			future.cancel(true);
			throw new AssertionError("작업이 끝나지 않았습니다.", e);
		}
	}

	/**
	 * 다른 연결이 그 문장(Hibernate가 만든 SQL이라 {@code ilike} 패턴으로)을 실행하다 행 잠금에서 기다릴 때까지 기다린다.
	 * @param queryLike 예: {@code "%insert into votes%"}
	 * @param count 기다리는 연결 수(둘이 같은 행을 기다리는 순서를 만들 때 2)
	 */
	protected void awaitLockWait(String queryLike, int count) {
		long deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000;
		while (System.currentTimeMillis() < deadline) {
			Long waiting = jdbcTemplate.queryForObject("""
					select count(*) from pg_stat_activity
					where datname = current_database() and wait_event_type = 'Lock' and query ilike ?""", Long.class,
					queryLike);
			if (waiting != null && waiting >= count) {
				return;
			}
			sleep(20);
		}
		fail("잠금 대기가 생기지 않았습니다: " + queryLike + "\n지금 연결들:\n" + activity());
	}

	/** 디버깅용: 이 DB의 다른 연결 상태 */
	protected String activity() {
		StringBuilder out = new StringBuilder();
		jdbcTemplate.queryForList("""
				select state, wait_event_type, wait_event, left(regexp_replace(query, '\\s+', ' ', 'g'), 160) as query
				from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid()""")
			.forEach(row -> out.append("  ").append(row).append('\n'));
		return out.toString();
	}

	protected void awaitLockWait(String queryLike) {
		awaitLockWait(queryLike, 1);
	}

	/** 지금 풀에서 빌려 간 커넥션 수(HikariCP) */
	protected int activeConnections() {
		try {
			return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
		}
		catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 조건이 참이 될 때까지 짧게 기다린다. */
	protected void awaitTrue(String what, java.util.function.BooleanSupplier condition) {
		long deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			sleep(20);
		}
		fail("기다리던 조건이 참이 되지 않았습니다: " + what);
	}

	protected static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** 하위 클래스의 @AfterEach라 IntegrationTest.cleanDatabase(TRUNCATE)보다 먼저 돈다. 잠금을 모두 풀어야 TRUNCATE가 진행된다. */
	@AfterEach
	void releaseAll() throws InterruptedException {
		for (RowLock lock : locks) {
			lock.release.countDown();
			lock.done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		locks.clear();
		executor.shutdownNow();
		executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}
}
