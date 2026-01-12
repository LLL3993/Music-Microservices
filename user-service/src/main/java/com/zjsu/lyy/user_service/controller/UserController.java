package com.zjsu.lyy.user_service.controller;

import com.zjsu.lyy.user_service.dto.CreateUserRequest;
import com.zjsu.lyy.user_service.dto.UpdateUserRequest;
import com.zjsu.lyy.user_service.dto.UserResponse;
import com.zjsu.lyy.user_service.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private static final Logger log = LoggerFactory.getLogger(UserController.class);

	@Value("${server.port:unknown}")
	private String serverPort;

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
		UserResponse created = userService.createUser(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/username")
	public UserResponse getByUsernameQuery(@RequestParam String username) {
		log.info("Request handled by user-service instance on port: {}", serverPort);
		return userService.getUserByUsername(username);
	}

	@GetMapping
	public ResponseEntity<List<UserResponse>> listAll(@RequestHeader(value = "X-User-Is-Admin", required = false) String isAdmin) {
		if (!"true".equalsIgnoreCase(String.valueOf(isAdmin))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(userService.listUsers());
	}

	@PutMapping("/{id}")
	public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateUser(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		userService.deleteUser(id);
		return ResponseEntity.noContent().build();
	}
}
