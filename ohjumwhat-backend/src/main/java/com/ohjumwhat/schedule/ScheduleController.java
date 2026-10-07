package com.ohjumwhat.schedule;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

@RestController
public class ScheduleController {

	private final ScheduleService scheduleService;

	public ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@GetMapping("/api/orgs/{orgId}/schedules")
	List<ScheduleResponse> list(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId) {
		return scheduleService.list(orgId, loginUser.getUserId());
	}

	@Restricted(Restriction.POLL)
	@PostMapping("/api/orgs/{orgId}/schedules")
	@ResponseStatus(HttpStatus.CREATED)
	ScheduleResponse create(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@Valid @RequestBody ScheduleRequest request) {
		return scheduleService.create(orgId, loginUser.getUserId(), request);
	}

	@Restricted(Restriction.POLL)
	@PutMapping("/api/orgs/{orgId}/schedules/{scheduleId}")
	ScheduleResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@PathVariable Long scheduleId, @Valid @RequestBody ScheduleRequest request) {
		return scheduleService.update(orgId, scheduleId, loginUser.getUserId(), request);
	}

	@Restricted(Restriction.POLL)
	@DeleteMapping("/api/orgs/{orgId}/schedules/{scheduleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@PathVariable Long scheduleId) {
		scheduleService.delete(orgId, scheduleId, loginUser.getUserId());
	}
}
