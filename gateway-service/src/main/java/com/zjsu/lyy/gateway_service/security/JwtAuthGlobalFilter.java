package com.zjsu.lyy.gateway_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

	private final byte[] secretBytes;
	private final String issuer;

	public JwtAuthGlobalFilter(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.issuer}") String issuer
	) {
		this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		this.issuer = issuer;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
			return chain.filter(exchange);
		}

		String path = exchange.getRequest().getURI().getPath();
		if (isPermitPath(path)) {
			return chain.filter(exchange);
		}

		String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return unauthorized(exchange, "缺少或非法 Authorization");
		}

		String token = authHeader.substring("Bearer ".length()).trim();
		if (token.isEmpty()) {
			return unauthorized(exchange, "缺少或非法 Authorization");
		}

		Claims claims;
		try {
			claims = Jwts.parser()
					.verifyWith(Keys.hmacShaKeyFor(secretBytes))
					.requireIssuer(issuer)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		}
		catch (JwtException ex) {
			return unauthorized(exchange, "JWT 校验失败");
		}

		String username = claims.getSubject();
		Object userId = claims.get("uid");
		Object email = claims.get("email");
		Object admin = claims.get("admin");

		ServerHttpRequest mutated = exchange.getRequest()
				.mutate()
				.header("X-Username", username == null ? "" : username)
				.header("X-User-Id", userId == null ? "" : String.valueOf(userId))
				.header("X-User-Email", email == null ? "" : String.valueOf(email))
				.header("X-User-Is-Admin", admin == null ? "" : String.valueOf(admin))
				.build();

		return chain.filter(exchange.mutate().request(mutated).build());
	}

	@Override
	public int getOrder() {
		return -100;
	}

	private static boolean isPermitPath(String path) {
		return "/api/auth/login".equals(path) || "/api/auth/register".equals(path);
	}

	private static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
		String path = exchange.getRequest().getURI().getPath();
		String json = """
				{"timestamp":"%s","status":401,"message":"%s","path":"%s"}
				""".formatted(Instant.now().toString(), escapeJson(message), escapeJson(path));

		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
	}

	private static String escapeJson(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
