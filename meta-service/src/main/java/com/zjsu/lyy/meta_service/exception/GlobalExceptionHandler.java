package com.zjsu.lyy.meta_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiError(Instant.now(), HttpStatus.NOT_FOUND.value(), ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError(Instant.now(), HttpStatus.CONFLICT.value(), ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError(Instant.now(), HttpStatus.CONFLICT.value(), "数据冲突（可能是 songName 重复）", request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "参数校验失败", request.getRequestURI()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiError(Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误", request.getRequestURI()));
	}
}
