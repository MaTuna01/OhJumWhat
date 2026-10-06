plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ohjumwhat"
version = "1.11.1"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.flywaydb:flyway-database-postgresql")
	// 웹 푸시(FCM)만 쓴다. Firestore·Storage·Realtime Database(Netty)는 쓰지 않으므로 빼서 이미지를 가볍게 둔다.
	implementation("com.google.firebase:firebase-admin:9.11.0") {
		exclude(group = "com.google.cloud", module = "google-cloud-firestore")
		exclude(group = "com.google.cloud", module = "google-cloud-storage")
		exclude(group = "io.netty")
	}
	// SDK가 기본으로 쓰는 JSON 처리(JacksonFactory)는 빼 둔 Firestore 쪽에서 들어왔다. 없으면 FCM 클라이언트를 만들 때
	// NoClassDefFoundError가 난다. Jackson 2는 core만 들어오고, 패키지가 달라 Spring의 Jackson 3와 섞이지 않는다.
	// 버전은 firebase-admin이 쓰는 google-http-client와 같게 둔다(firebase-admin을 올리면
	// ./gradlew dependencies에서 google-http-client 버전을 보고 함께 올린다).
	implementation("com.google.http-client:google-http-client-jackson2:2.2.0")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 프로필 사진 테스트가 Java2D로 그림을 그린다. macOS에서 창(Dock 아이콘)을 띄우지 않게 헤드리스로 돌린다.
	systemProperty("java.awt.headless", "true")
}
