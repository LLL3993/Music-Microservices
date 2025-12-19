package com.zjsu.lyy.list_service.controller;

import com.zjsu.lyy.list_service.dto.CreateFavoriteRequest;
import com.zjsu.lyy.list_service.dto.FavoriteResponse;
import com.zjsu.lyy.list_service.service.FavoriteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

	private final FavoriteService favoriteService;

	public FavoriteController(FavoriteService favoriteService) {
		this.favoriteService = favoriteService;
	}

	@PostMapping
	public ResponseEntity<FavoriteResponse> createFavorite(
			@RequestHeader("X-Username") String username,
			@Valid @RequestBody CreateFavoriteRequest request
	) {
		FavoriteResponse created = favoriteService.createFavorite(username, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/username")
	public List<FavoriteResponse> listByUsernameQuery(@RequestHeader("X-Username") String username) {
		return favoriteService.listFavoritesByUsername(username);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteFavorite(@PathVariable Long id) {
		favoriteService.deleteFavorite(id);
		return ResponseEntity.noContent().build();
	}
}
