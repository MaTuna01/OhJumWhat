package com.ohjumwhat.poll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PollRepository extends JpaRepository<Poll, Long> {

	boolean existsByScheduleIdAndPollDate(Long scheduleId, LocalDate pollDate);

	/** 조직 홈의 "오늘 열린 투표" (poll_date = 오늘, 한국 날짜) */
	List<Poll> findByOrganizationIdAndPollDateOrderByOpensAtAscIdAsc(Long organizationId, LocalDate pollDate);

	/** 지난 투표: 오늘(한국 날짜) 이전 투표. 정렬은 Pageable로 준다. */
	Slice<Poll> findByOrganizationIdAndPollDateBefore(Long organizationId, LocalDate pollDate, Pageable pageable);

	/** 오늘(한국 날짜) 열려 있고 아직 마감되지 않은 투표가 있는 조직 ID */
	@Query("""
			select distinct p.organizationId from Poll p
			where p.organizationId in :organizationIds
			  and p.pollDate = :today and p.opensAt <= :now and p.closesAt > :now""")
	List<Long> findOrganizationIdsWithOpenPoll(Collection<Long> organizationIds, LocalDate today, Instant now);
}
