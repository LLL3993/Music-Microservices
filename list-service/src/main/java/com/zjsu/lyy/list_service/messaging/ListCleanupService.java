package com.zjsu.lyy.list_service.messaging;

import com.zjsu.lyy.list_service.repository.FavoriteRepository;
import com.zjsu.lyy.list_service.repository.PlaylistDetailRepository;
import com.zjsu.lyy.list_service.repository.PlaylistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListCleanupService {

	private final FavoriteRepository favoriteRepository;
	private final PlaylistRepository playlistRepository;
	private final PlaylistDetailRepository playlistDetailRepository;

	public ListCleanupService(
			FavoriteRepository favoriteRepository,
			PlaylistRepository playlistRepository,
			PlaylistDetailRepository playlistDetailRepository
	) {
		this.favoriteRepository = favoriteRepository;
		this.playlistRepository = playlistRepository;
		this.playlistDetailRepository = playlistDetailRepository;
	}

	@Transactional
	public void cleanupByUsername(String username) {
		playlistDetailRepository.deleteAllByUsername(username);
		playlistRepository.deleteAllByUsername(username);
		favoriteRepository.deleteAllByUsername(username);
	}

	@Transactional
	public void cleanupBySongName(String songName) {
		playlistDetailRepository.deleteAllBySongName(songName);
		favoriteRepository.deleteAllBySongName(songName);
	}
}

