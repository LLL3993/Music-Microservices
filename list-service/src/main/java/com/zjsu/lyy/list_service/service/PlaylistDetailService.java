package com.zjsu.lyy.list_service.service;

import com.zjsu.lyy.list_service.dto.CreatePlaylistDetailRequest;
import com.zjsu.lyy.list_service.dto.PlaylistDetailResponse;
import com.zjsu.lyy.list_service.entity.PlaylistDetail;
import com.zjsu.lyy.list_service.exception.ConflictException;
import com.zjsu.lyy.list_service.exception.NotFoundException;
import com.zjsu.lyy.list_service.integration.MetaClient;
import com.zjsu.lyy.list_service.integration.UserClient;
import com.zjsu.lyy.list_service.repository.PlaylistDetailRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaylistDetailService {

	private final PlaylistDetailRepository playlistDetailRepository;
	private final PlaylistService playlistService;
	private final UserClient userClient;
	private final MetaClient metaClient;

	public PlaylistDetailService(
			PlaylistDetailRepository playlistDetailRepository,
			PlaylistService playlistService,
			UserClient userClient,
			MetaClient metaClient
	) {
		this.playlistDetailRepository = playlistDetailRepository;
		this.playlistService = playlistService;
		this.userClient = userClient;
		this.metaClient = metaClient;
	}

	@Transactional
	public PlaylistDetailResponse createDetail(String username, CreatePlaylistDetailRequest request) {
		userClient.assertUserExists(username);
		metaClient.assertSongExists(request.songName());
		playlistService.getPlaylistEntityByUsernameAndName(username, request.playlistName());

		if (playlistDetailRepository.existsByUsernameAndPlaylistNameAndSongName(
				username, request.playlistName(), request.songName()
		)) {
			throw new ConflictException("歌曲已在歌单中");
		}

		PlaylistDetail detail = new PlaylistDetail();
		detail.setUsername(username);
		detail.setPlaylistName(request.playlistName());
		detail.setSongName(request.songName());

		PlaylistDetail saved = playlistDetailRepository.save(detail);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<PlaylistDetailResponse> listDetails(String username, String playlistName) {
		userClient.assertUserExists(username);
		playlistService.getPlaylistEntityByUsernameAndName(username, playlistName);

		return playlistDetailRepository.findAllByUsernameAndPlaylistNameOrderByIdDesc(username, playlistName)
				.stream()
				.map(PlaylistDetailService::toResponse)
				.toList();
	}

	@Transactional
	public void deleteDetail(Long id) {
		if (!playlistDetailRepository.existsById(id)) {
			throw new NotFoundException("歌单详情不存在");
		}
		playlistDetailRepository.deleteById(id);
	}

	private static PlaylistDetailResponse toResponse(PlaylistDetail detail) {
		return new PlaylistDetailResponse(
				detail.getId(),
				detail.getUsername(),
				detail.getPlaylistName(),
				detail.getSongName()
		);
	}
}
