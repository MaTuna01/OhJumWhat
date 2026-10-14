package com.ohjumwhat.organization;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.INVITE)
@RestController
public class InviteController {

	private final InviteService inviteService;

	public InviteController(InviteService inviteService) {
		this.inviteService = inviteService;
	}

	@Operation(summary = "초대 링크 보기", description = "초대 수락 화면(D02)에 보여줄 조직 이름·인원과 내가 이미 멤버인지. "
			+ "초대 링크는 /invite/{token}이고, 로그인하지 않았으면 화면이 경로를 기억해 두었다가 로그인 뒤 돌아온다.")
	@ErrorCase(status = 404, message = "유효하지 않은 초대 링크예요.", when = "그 토큰의 조직이 없을 때")
	@GetMapping("/api/invites/{token}")
	InviteResponse get(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "초대 토큰(조직 정보의 inviteToken, 43자)", example = "q3Zt8sKpX1vYb2Lm9NcRw4JhFe6UaDiG0oTyQ7lVnMk")
			@PathVariable String token) {
		return inviteService.get(token, loginUser.getUserId());
	}

	@Operation(summary = "초대 수락", description = "초대 링크로 조직에 들어간다(D02 「참여하기」). 이미 멤버면 아무것도 하지 않고 같은 응답을 준다. "
			+ "화면은 응답의 조직 홈으로 이동한다.")
	@ErrorCase(status = 404, message = "유효하지 않은 초대 링크예요.", when = "그 토큰의 조직이 없을 때")
	@ErrorCase(status = 409, message = "다른 사람의 변경과 겹쳤어요. 다시 시도해 주세요.", when = "같은 사람이 동시에 두 번 참여했을 때")
	@Restricted(Restriction.SUSPEND)
	@PostMapping("/api/invites/{token}/join")
	JoinResponse join(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "초대 토큰(조직 정보의 inviteToken, 43자)", example = "q3Zt8sKpX1vYb2Lm9NcRw4JhFe6UaDiG0oTyQ7lVnMk")
			@PathVariable String token) {
		return new JoinResponse(inviteService.join(token, loginUser.getUserId()));
	}

	@Schema(name = "InviteJoinResponse", description = "초대 수락 결과")
	record JoinResponse(@Schema(description = "들어간 조직 ID", example = "1") Long organizationId) {
	}
}
