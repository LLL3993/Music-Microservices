package com.zjsu.lyy.user_service.service;

import com.zjsu.lyy.user_service.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

@Service
@RefreshScope
public class JwtService {

	private final byte[] secretBytes;
	private final String issuer;
	private final long expirationSeconds;

	public JwtService(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.issuer}") String issuer,
			@Value("${security.jwt.expiration-seconds}") long expirationSeconds
	) {
		this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		this.issuer = issuer;
		this.expirationSeconds = expirationSeconds;
	}

	public String issueToken(User user) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(expirationSeconds);

		return Jwts.builder()
				.issuer(issuer)
				.subject(user.getUsername())
				.issuedAt(Date.from(now))
				.expiration(Date.from(exp))
				.claim("uid", user.getId())
				.claim("email", user.getEmail())
				.claim("admin", user.isAdmin())
				.signWith(Keys.hmacShaKeyFor(secretBytes))
				.compact();
	}
}
