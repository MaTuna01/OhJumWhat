package com.ohjumwhat;

import org.springframework.boot.SpringApplication;

public class TestOhjumwhatApplication {

	public static void main(String[] args) {
		SpringApplication.from(OhjumwhatApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
