package com.zjsu.lyy.list_service.messaging;

import com.zjsu.lyy.list_service.inbox.InboxService;
import com.zjsu.lyy.list_service.repository.FavoriteRepository;
import com.zjsu.lyy.list_service.repository.PlaylistDetailRepository;
import com.zjsu.lyy.list_service.repository.PlaylistRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("docker")
public class ListEventHandlerService {

	private final InboxService inboxService;
	private final FavoriteRepository favoriteRepository;
	private final PlaylistRepository playlistRepository;
	private final PlaylistDetailRepository playlistDetailRepository;

	public ListEventHandlerService(
			InboxService inboxService,
			FavoriteRepository favoriteRepository,
			PlaylistRepository playlistRepository,
			PlaylistDetailRepository playlistDetailRepository
	) {
		this.inboxService = inboxService;
		this.favoriteRepository = favoriteRepository;
		this.playlistRepository = playlistRepository;
		this.playlistDetailRepository = playlistDetailRepository;
	}

	@Transactional
	public boolean handleUserDeleted(UserDeletedEvent event) {
		if (event == null || event.username() == null || event.username().isBlank()) {
			return false;
		}
		boolean first = inboxService.tryMarkProcessed(event.eventId(), RabbitMqConstants.ROUTING_KEY_USER_DELETED);
		if (!first) return false;

		playlistDetailRepository.deleteAllByUsername(event.username());
		playlistRepository.deleteAllByUsername(event.username());
		favoriteRepository.deleteAllByUsername(event.username());
		return true;
	}

	@Transactional
	public boolean handleSongDeleted(SongDeletedEvent event) {
		if (event == null || event.songName() == null || event.songName().isBlank()) {
			return false;
		}
		boolean first = inboxService.tryMarkProcessed(event.eventId(), RabbitMqConstants.ROUTING_KEY_SONG_DELETED);
		if (!first) return false;

		playlistDetailRepository.deleteAllBySongName(event.songName());
		favoriteRepository.deleteAllBySongName(event.songName());
		return true;
	}
}

