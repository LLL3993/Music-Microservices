package com.zjsu.lyy.list_service.service;

import com.zjsu.lyy.list_service.dto.CreatePlaylistRequest;
import com.zjsu.lyy.list_service.dto.PlaylistResponse;
import com.zjsu.lyy.list_service.dto.UpdatePlaylistRequest;
import com.zjsu.lyy.list_service.entity.Playlist;
import com.zjsu.lyy.list_service.exception.ConflictException;
import com.zjsu.lyy.list_service.exception.NotFoundException;
import com.zjsu.lyy.list_service.integration.UserClient;
import com.zjsu.lyy.list_service.repository.PlaylistDetailRepository;
import com.zjsu.lyy.list_service.repository.PlaylistRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RefreshScope
public class PlaylistService {

	private final PlaylistRepository playlistRepository;
	private final PlaylistDetailRepository playlistDetailRepository;
	private final UserClient userClient;
	private boolean validationEnabled;

	public PlaylistService(
			PlaylistRepository playlistRepository,
			PlaylistDetailRepository playlistDetailRepository,
			UserClient userClient,
			@Value("${services.validation.enabled:true}") boolean validationEnabled
	) {
		this.playlistRepository = playlistRepository;
		this.playlistDetailRepository = playlistDetailRepository;
		this.userClient = userClient;
		this.validationEnabled = validationEnabled;
	}

	@Transactional
	public PlaylistResponse createPlaylist(String username, CreatePlaylistRequest request) {
		assertUserExists(username);

		if (playlistRepository.existsByUsernameAndPlaylistName(username, request.playlistName())) {
			throw new ConflictException("歌单已存在");
		}

		Playlist playlist = new Playlist();
		playlist.setUsername(username);
		playlist.setPlaylistName(request.playlistName());
		playlist.setDescription(request.description());
		playlist.setPublic(request.isPublic() == null || request.isPublic());

		Playlist saved = playlistRepository.save(playlist);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<PlaylistResponse> listPlaylistsByUsername(String username) {
		assertUserExists(username);
		return playlistRepository.findAllByUsernameOrderByIdDesc(username)
				.stream()
				.map(PlaylistService::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PlaylistResponse> listPlaylistsByPlaylistName(String playlistName) {
		return playlistRepository.findAllByPlaylistNameOrderByIdDesc(playlistName)
				.stream()
				.map(PlaylistService::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PlaylistResponse> listPublicPlaylists(int limit) {
		int size = Math.max(1, Math.min(limit, 50));
		return playlistRepository.findAllByIsPublicTrueOrderByIdAsc(PageRequest.of(0, size))
				.stream()
				.map(PlaylistService::toResponse)
				.toList();
	}

	@Transactional
	public PlaylistResponse updatePlaylist(Long id, UpdatePlaylistRequest request) {
		Playlist playlist = playlistRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("歌单不存在"));

		if (playlistRepository.existsByUsernameAndPlaylistNameAndIdNot(playlist.getUsername(), request.playlistName(), id)) {
			throw new ConflictException("歌单已存在");
		}

		String oldPlaylistName = playlist.getPlaylistName();
		String newPlaylistName = request.playlistName();

		playlist.setPlaylistName(request.playlistName());
		playlist.setDescription(request.description());
		playlist.setPublic(Boolean.TRUE.equals(request.isPublic()));

		Playlist saved = playlistRepository.save(playlist);

		if (!oldPlaylistName.equals(newPlaylistName)) {
			var details = playlistDetailRepository.findAllByUsernameAndPlaylistName(playlist.getUsername(), oldPlaylistName);
			if (!details.isEmpty()) {
				details.forEach(d -> d.setPlaylistName(newPlaylistName));
				playlistDetailRepository.saveAll(details);
			}
		}

		return toResponse(saved);
	}

	@Transactional
	public void deletePlaylist(Long id) {
		Playlist playlist = playlistRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("歌单不存在"));

		playlistDetailRepository.deleteAllByUsernameAndPlaylistName(playlist.getUsername(), playlist.getPlaylistName());
		playlistRepository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public Playlist getPlaylistEntityByUsernameAndName(String username, String playlistName) {
		return playlistRepository.findByUsernameAndPlaylistName(username, playlistName)
				.orElseThrow(() -> new NotFoundException("歌单不存在"));
	}

	private static PlaylistResponse toResponse(Playlist playlist) {
		return new PlaylistResponse(
				playlist.getId(),
				playlist.getPlaylistName(),
				playlist.getUsername(),
				playlist.getDescription(),
				playlist.isPublic()
		);
	}

	private void assertUserExists(String username) {
		if (!validationEnabled) {
			return;
		}
		userClient.assertUserExists(username);
	}
}
