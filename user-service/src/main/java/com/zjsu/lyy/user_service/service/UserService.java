package com.zjsu.lyy.user_service.service;

import com.zjsu.lyy.user_service.dto.CreateUserRequest;
import com.zjsu.lyy.user_service.dto.UpdateUserRequest;
import com.zjsu.lyy.user_service.dto.UserResponse;
import com.zjsu.lyy.user_service.entity.User;
import com.zjsu.lyy.user_service.exception.ConflictException;
import com.zjsu.lyy.user_service.exception.NotFoundException;
import com.zjsu.lyy.user_service.messaging.UserEventPublisher;
import com.zjsu.lyy.user_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserEventPublisher userEventPublisher;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserEventPublisher userEventPublisher) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.userEventPublisher = userEventPublisher;
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new ConflictException("username 已存在");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new ConflictException("email 已存在");
		}

		User user = new User();
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setAdmin(Boolean.TRUE.equals(request.isAdmin()));

		User saved = userRepository.save(user);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public UserResponse getUserByUsername(String username) {
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new NotFoundException("用户不存在"));
		return toResponse(user);
	}

	@Transactional
	public UserResponse updateUser(Long id, UpdateUserRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("用户不存在"));

		if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
			throw new ConflictException("username 已存在");
		}
		if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
			throw new ConflictException("email 已存在");
		}

		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setPassword(request.password());
		user.setAdmin(Boolean.TRUE.equals(request.isAdmin()));

		User saved = userRepository.save(user);
		return toResponse(saved);
	}

	@Transactional
	public void deleteUser(Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("用户不存在"));
		String username = user.getUsername();
		userRepository.deleteById(id);
		userEventPublisher.publishUserDeleted(username);
	}

	private static UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isAdmin());
	}
}
