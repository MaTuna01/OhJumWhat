package com.ohjumwhat.common;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 그 밖의 예외는 Spring Boot 기본 에러 처리(500)에 맡긴다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		log.debug("API 예외: status={}, message={}", e.getStatus().value(), e.getMessage());
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(FieldError::getDefaultMessage)
			.orElse("입력값을 확인해 주세요.");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
	}

	/** 동시에 들어온 요청이 DB 제약(UNIQUE·FK)에 걸린 경우. 예: 같은 메뉴를 동시에 추가 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException e) {
		log.warn("DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse("다른 사람의 변경과 겹쳤어요. 다시 시도해 주세요."));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("요청 형식이 올바르지 않아요."));
	}
}
