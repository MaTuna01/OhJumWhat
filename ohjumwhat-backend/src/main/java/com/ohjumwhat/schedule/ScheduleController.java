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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.SCHEDULE)
@RestController
public class ScheduleController {

	private final ScheduleService scheduleService;

	public ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@Operation(summary = "정기 투표 목록", description = "정기 투표 화면(D06)과 조직 홈의 정기 투표 요약. 오픈 시각이 이른 순이다. "
			+ "name의 `${오늘날짜}` 토큰은 그대로 오므로 화면이 「오늘 날짜」 칩으로 보여준다(06-D).")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}/schedules")
	List<ScheduleResponse> list(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId) {
		return scheduleService.list(orgId, loginUser.getUserId());
	}

	@Operation(summary = "정기 투표 추가", description = "정기 투표 규칙을 만든다(06-M·06-M2). 서버가 매분 확인해 고른 요일의 openTime(한국)에 "
			+ "규칙 이름을 제목으로 투표를 열고 closeTime에 마감한다. 서버가 오픈 시각에 꺼져 있었어도 마감 전에 켜지면 그날 투표가 열린다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = ScheduleName.TOO_LONG, when = "`${오늘날짜}`를 10자로 센 이름이 50자를 넘을 때")
	@ErrorCase(status = 400, message = "마감 시간은 오픈 시간보다 늦어야 해요.", when = "closeTime이 openTime보다 늦지 않을 때")
	@Restricted(Restriction.POLL)
	@PostMapping("/api/orgs/{orgId}/schedules")
	@ResponseStatus(HttpStatus.CREATED)
	ScheduleResponse create(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Valid @RequestBody ScheduleRequest request) {
		return scheduleService.create(orgId, loginUser.getUserId(), request);
	}

	@Operation(summary = "정기 투표 수정", description = "규칙을 통째로 바꾼다. 앞으로 열릴 투표부터 적용되고, 이미 열린 투표는 그대로다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "정기 투표 규칙을 찾을 수 없어요.", when = "이 조직의 규칙이 아닐 때")
	@ErrorCase(status = 400, message = ScheduleName.TOO_LONG, when = "`${오늘날짜}`를 10자로 센 이름이 50자를 넘을 때")
	@ErrorCase(status = 400, message = "마감 시간은 오픈 시간보다 늦어야 해요.", when = "closeTime이 openTime보다 늦지 않을 때")
	@Restricted(Restriction.POLL)
	@PutMapping("/api/orgs/{orgId}/schedules/{scheduleId}")
	ScheduleResponse update(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "정기 투표 규칙 ID", example = "3") @PathVariable Long scheduleId,
			@Valid @RequestBody ScheduleRequest request) {
		return scheduleService.update(orgId, scheduleId, loginUser.getUserId(), request);
	}

	@Operation(summary = "정기 투표 삭제", description = "규칙을 지운다. 이미 열린 투표는 남는다(정기 투표 표시만 빠진다). 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "정기 투표 규칙을 찾을 수 없어요.", when = "이 조직의 규칙이 아닐 때")
	@Restricted(Restriction.POLL)
	@DeleteMapping("/api/orgs/{orgId}/schedules/{scheduleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "정기 투표 규칙 ID", example = "3") @PathVariable Long scheduleId) {
		scheduleService.delete(orgId, scheduleId, loginUser.getUserId());
	}
}
