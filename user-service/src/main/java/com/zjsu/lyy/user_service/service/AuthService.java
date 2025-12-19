package com.zjsu.lyy.user_service.service;

import com.zjsu.lyy.user_service.dto.AuthResponse;
import com.zjsu.lyy.user_service.dto.LoginRequest;
import com.zjsu.lyy.user_service.dto.RegisterRequest;
import com.zjsu.lyy.user_service.dto.UserResponse;
import com.zjsu.lyy.user_service.entity.User;
import com.zjsu.lyy.user_service.exception.ConflictException;
import com.zjsu.lyy.user_service.exception.UnauthorizedException;
import com.zjsu.lyy.user_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
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
		user.setAdmin(false);

		User saved = userRepository.save(user);
		String token = jwtService.issueToken(saved);
		return new AuthResponse(token, toResponse(saved));
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByUsername(request.username())
				.orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

		String stored = user.getPassword();
		boolean ok;
		boolean needsUpgrade = false;
		if (stored != null && stored.startsWith("$2")) {
			ok = passwordEncoder.matches(request.password(), stored);
		}
		else {
			ok = request.password().equals(stored);
			needsUpgrade = ok;
		}
		if (!ok) throw new UnauthorizedException("用户名或密码错误");
		if (needsUpgrade) user.setPassword(passwordEncoder.encode(request.password()));

		String token = jwtService.issueToken(user);
		return new AuthResponse(token, toResponse(user));
	}

	private static UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isAdmin());
	}
}
