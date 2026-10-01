package com.ohjumwhat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OhjumwhatApplication {

	public static void main(String[] args) {
		SpringApplication.run(OhjumwhatApplication.class, args);
	}

}
