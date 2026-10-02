package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** MockMvc는 업로드 한도를 검사하지 않으므로(요청을 직접 만든다) 413 응답은 핸들러를 직접 불러 확인한다. */
class GlobalExceptionHandlerTest {

	@Test
	void 업로드_한도를_넘으면_413과_메시지를_준다() {
		var response = new GlobalExceptionHandler().handleTooLarge(new MaxUploadSizeExceededException(2 * 1024 * 1024));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getBody()).isEqualTo(new ErrorResponse("사진이 너무 커요."));
	}
}
