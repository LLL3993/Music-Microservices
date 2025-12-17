package com.zjsu.lyy.user_service.service;

import com.zjsu.lyy.user_service.dto.CreateUserRequest;
import com.zjsu.lyy.user_service.dto.UpdateUserRequest;
import com.zjsu.lyy.user_service.dto.UserResponse;
import com.zjsu.lyy.user_service.entity.User;
import com.zjsu.lyy.user_service.exception.ConflictException;
import com.zjsu.lyy.user_service.exception.NotFoundException;
import com.zjsu.lyy.user_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		if (userRepository.existsByUsernameAndDeletedFalse(request.username())) {
			throw new ConflictException("username 已存在");
		}
		if (userRepository.existsByEmailAndDeletedFalse(request.email())) {
			throw new ConflictException("email 已存在");
		}

		User user = new User();
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setPassword(request.password());
		user.setAdmin(Boolean.TRUE.equals(request.isAdmin()));
		user.setDeleted(false);

		User saved = userRepository.save(user);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public UserResponse getUserByUsername(String username) {
		User user = userRepository.findByUsernameAndDeletedFalse(username)
				.orElseThrow(() -> new NotFoundException("用户不存在"));
		return toResponse(user);
	}

	@Transactional
	public UserResponse updateUser(Long id, UpdateUserRequest request) {
		User user = userRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new NotFoundException("用户不存在"));

		if (userRepository.existsByUsernameAndDeletedFalseAndIdNot(request.username(), id)) {
			throw new ConflictException("username 已存在");
		}
		if (userRepository.existsByEmailAndDeletedFalseAndIdNot(request.email(), id)) {
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
		User user = userRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new NotFoundException("用户不存在"));
		user.setDeleted(true);
		userRepository.save(user);
	}

	private static UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isAdmin(), user.isDeleted());
	}
}
