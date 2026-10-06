package com.ohjumwhat.push;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 푸시를 받을 기기(push_devices, Flyway V20). 기기는 로그인 세션(spring_session)에 묶여 있어 로그아웃·세션 만료·회원 삭제 때
 * DB가 함께 지운다. 등록은 세션 행을 찾는 것과 upsert를 한 문장으로 해서 세션이 없으면 아무것도 쓰지 않는다.
 */
@Repository
class PushDeviceRepository {

	private final JdbcClient jdbc;

	PushDeviceRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 그 로그인(세션 ID = spring_session.session_id)의 기기로 등록한다. 같은 FID가 있으면 사람·세션·시각을 옮긴다
	 * (같은 브라우저에서 다른 계정이 켜면 그 사람에게 간다).
	 *
	 * @return 새 행이면 true, 있던 행을 고쳤으면 false. 세션 행이 없으면 비어 있다(아무것도 쓰지 않았다)
	 */
	Optional<Boolean> upsert(Long userId, String fid, String sessionId, Instant now) {
		return jdbc.sql("""
				insert into push_devices (user_id, fid, session_primary_id, created_at, last_seen_at)
				select :userId, :fid, s.primary_id, :now, :now
				from spring_session s
				where s.session_id = :sessionId
				on conflict (fid) do update set user_id = excluded.user_id,
					session_primary_id = excluded.session_primary_id, last_seen_at = excluded.last_seen_at
				returning (xmax = 0) as created""")
			.param("userId", userId)
			.param("fid", fid)
			.param("sessionId", sessionId)
			.param("now", timestamp(now))
			.query(Boolean.class)
			.optional();
	}

	/** 그 사람의 기기를 최근(last_seen_at) max대만 남기고 지운다. */
	int keepRecent(Long userId, int max) {
		return jdbc.sql("""
				delete from push_devices
				where user_id = :userId
				  and id not in (select id from push_devices where user_id = :userId
				                 order by last_seen_at desc, id desc limit :max)""")
			.param("userId", userId)
			.param("max", max)
			.update();
	}

	/** 그 사람의 기기만 지운다(다른 사람에게 옮겨 간 FID는 그대로). */
	int delete(Long userId, String fid) {
		return jdbc.sql("delete from push_devices where user_id = :userId and fid = :fid")
			.param("userId", userId)
			.param("fid", fid)
			.update();
	}

	/** 그 사람의 기기(최근 순) */
	List<String> findFids(Long userId) {
		return jdbc.sql("select fid from push_devices where user_id = :userId order by last_seen_at desc, id desc")
			.param("userId", userId)
			.query(String.class)
			.list();
	}

	/**
	 * 더는 받을 수 없는 기기를 지운다. 보내는 사이에 다시 등록한 기기(readAt보다 뒤에 등록)는 남긴다.
	 *
	 * @param readAt 보낼 기기를 읽기 직전 시각
	 */
	int deleteStale(Collection<String> fids, Instant readAt) {
		return jdbc.sql("delete from push_devices where fid in (:fids) and last_seen_at <= :readAt")
			.param("fids", fids)
			.param("readAt", timestamp(readAt))
			.update();
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
