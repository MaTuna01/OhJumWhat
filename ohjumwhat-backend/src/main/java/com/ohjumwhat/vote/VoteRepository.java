package com.ohjumwhat.vote;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VoteRepository extends JpaRepository<Vote, Long> {

	/** 참여 순서(마지막으로 바꾼 시각)대로. 메뉴를 옮기면 새 메뉴 명단의 끝에 붙는다. */
	List<Vote> findByPollIdOrderByUpdatedAtAscIdAsc(Long pollId);

	long countByOptionId(Long optionId);

	/**
	 * 한 사람 한 표: (poll_id, user_id) 행이 있으면 option_id만 바꾼다. option_id가 NULL이면 "오늘은 패스".
	 * 동시에 눌러도 중복 행이 생기지 않도록 DB의 ON CONFLICT로 처리한다.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			insert into votes (poll_id, user_id, option_id, created_at, updated_at)
			values (:pollId, :userId, cast(:optionId as bigint), now(), now())
			on conflict (poll_id, user_id)
			do update set option_id = excluded.option_id, updated_at = now()""", nativeQuery = true)
	void upsert(Long pollId, Long userId, Long optionId);

	/** 탈퇴한 멤버의 응답 중 아직 마감되지 않은 투표의 것만 지운다. 마감된 투표 기록은 남긴다. */
	@Modifying
	@Query("""
			delete from Vote v
			where v.userId = :userId
			  and v.pollId in (select p.id from com.ohjumwhat.poll.Poll p
			                   where p.organizationId = :organizationId and p.closesAt > :now)""")
	int deleteInOpenPolls(Long organizationId, Long userId, Instant now);

	/** 관리자가 메뉴를 강제로 지울 때 그 메뉴의 응답부터 지운다(응답한 사람은 미응답이 된다). */
	@Modifying
	@Query("delete from Vote v where v.optionId = :optionId")
	int deleteByOptionId(Long optionId);
}
