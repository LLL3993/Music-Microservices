package com.zjsu.lyy.list_service.controller;

import com.zjsu.lyy.list_service.dto.CreatePlaylistDetailRequest;
import com.zjsu.lyy.list_service.dto.PlaylistDetailResponse;
import com.zjsu.lyy.list_service.service.PlaylistDetailService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlist-details")
public class PlaylistDetailController {

	private final PlaylistDetailService playlistDetailService;

	public PlaylistDetailController(PlaylistDetailService playlistDetailService) {
		this.playlistDetailService = playlistDetailService;
	}

	@PostMapping
	public ResponseEntity<PlaylistDetailResponse> createDetail(
			@RequestHeader("X-Username") String username,
			@Valid @RequestBody CreatePlaylistDetailRequest request
	) {
		PlaylistDetailResponse created = playlistDetailService.createDetail(username, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping
	public List<PlaylistDetailResponse> listDetailsQuery(
			@RequestHeader(value = "X-Username", required = false) String authUsername,
			@RequestParam(required = false) String username,
			@RequestParam String playlistName
	) {
		boolean hasAuth = !(authUsername == null || authUsername.isBlank());
		boolean hasUsername = !(username == null || username.isBlank());
		if (!hasAuth) {
			return hasUsername
					? playlistDetailService.listPublicDetails(username, playlistName)
					: playlistDetailService.listPublicDetails(playlistName);
		}
		if (hasUsername && !username.equals(authUsername)) {
			return playlistDetailService.listPublicDetails(username, playlistName);
		}
		return playlistDetailService.listDetails(authUsername, playlistName);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteDetail(@PathVariable Long id) {
		playlistDetailService.deleteDetail(id);
		return ResponseEntity.noContent().build();
	}
}
