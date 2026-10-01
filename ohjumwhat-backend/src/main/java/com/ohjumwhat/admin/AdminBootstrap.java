package com.ohjumwhat.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.ohjumwhat.user.UserService;

/** 서버가 뜰 때 관리자 목록을 설정과 맞춘다. 이미 로그인해 있는 관리자도 다시 로그인하지 않고 바로 콘솔을 쓸 수 있다. */
@Component
class AdminBootstrap implements ApplicationRunner {

	private final UserService userService;

	AdminBootstrap(UserService userService) {
		this.userService = userService;
	}

	@Override
	public void run(ApplicationArguments args) {
		userService.syncAdmins();
	}
}
