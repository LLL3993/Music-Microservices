package com.zjsu.lyy.list_service.controller;

import com.zjsu.lyy.list_service.dto.CreatePlaylistRequest;
import com.zjsu.lyy.list_service.dto.PlaylistResponse;
import com.zjsu.lyy.list_service.dto.UpdatePlaylistRequest;
import com.zjsu.lyy.list_service.service.PlaylistService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

	private final PlaylistService playlistService;

	public PlaylistController(PlaylistService playlistService) {
		this.playlistService = playlistService;
	}

	@PostMapping
	public ResponseEntity<PlaylistResponse> createPlaylist(
			@RequestHeader("X-Username") String username,
			@Valid @RequestBody CreatePlaylistRequest request
	) {
		PlaylistResponse created = playlistService.createPlaylist(username, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/username")
	public List<PlaylistResponse> listByUsernameQuery(@RequestHeader("X-Username") String username) {
		return playlistService.listPlaylistsByUsername(username);
	}

	@GetMapping("/playlist-name")
	public List<PlaylistResponse> listByPlaylistNameQuery(@RequestParam String playlistName) {
		return playlistService.listPlaylistsByPlaylistName(playlistName);
	}

	@PutMapping("/{id}")
	public PlaylistResponse updatePlaylist(@PathVariable Long id, @Valid @RequestBody UpdatePlaylistRequest request) {
		return playlistService.updatePlaylist(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deletePlaylist(@PathVariable Long id) {
		playlistService.deletePlaylist(id);
		return ResponseEntity.noContent().build();
	}
}
