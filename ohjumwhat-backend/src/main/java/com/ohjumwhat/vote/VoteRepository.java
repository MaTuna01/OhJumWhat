package com.ohjumwhat.vote;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VoteRepository extends JpaRepository<Vote, Long> {

	/** 탈퇴한 멤버의 응답 중 아직 마감되지 않은 투표의 것만 지운다. 마감된 투표 기록은 남긴다. */
	@Modifying
	@Query("""
			delete from Vote v
			where v.userId = :userId
			  and v.pollId in (select p.id from com.ohjumwhat.poll.Poll p
			                   where p.organizationId = :organizationId and p.closesAt > :now)""")
	int deleteInOpenPolls(Long organizationId, Long userId, Instant now);
}
