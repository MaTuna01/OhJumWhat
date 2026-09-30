package com.ohjumwhat.poll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PollRepository extends JpaRepository<Poll, Long> {

	/** 오늘(한국 날짜) 열려 있고 아직 마감되지 않은 투표가 있는 조직 ID */
	@Query("""
			select distinct p.organizationId from Poll p
			where p.organizationId in :organizationIds
			  and p.pollDate = :today and p.opensAt <= :now and p.closesAt > :now""")
	List<Long> findOrganizationIdsWithOpenPoll(Collection<Long> organizationIds, LocalDate today, Instant now);
}
