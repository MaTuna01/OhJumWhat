package com.ohjumwhat.schedule;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PollScheduleRepository extends JpaRepository<PollSchedule, Long> {

	List<PollSchedule> findByOrganizationIdOrderByOpenTimeAscIdAsc(Long organizationId);
}
