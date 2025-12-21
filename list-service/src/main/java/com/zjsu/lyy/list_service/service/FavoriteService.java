package com.zjsu.lyy.list_service.service;

import com.zjsu.lyy.list_service.dto.CreateFavoriteRequest;
import com.zjsu.lyy.list_service.dto.FavoriteResponse;
import com.zjsu.lyy.list_service.entity.Favorite;
import com.zjsu.lyy.list_service.exception.ConflictException;
import com.zjsu.lyy.list_service.exception.NotFoundException;
import com.zjsu.lyy.list_service.integration.MetaClient;
import com.zjsu.lyy.list_service.integration.UserClient;
import com.zjsu.lyy.list_service.repository.FavoriteRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final UserClient userClient;
	private final MetaClient metaClient;
	private final boolean validationEnabled;

	public FavoriteService(
			FavoriteRepository favoriteRepository,
			UserClient userClient,
			MetaClient metaClient,
			@Value("${services.validation.enabled:true}") boolean validationEnabled
	) {
		this.favoriteRepository = favoriteRepository;
		this.userClient = userClient;
		this.metaClient = metaClient;
		this.validationEnabled = validationEnabled;
	}

	@Transactional
	public FavoriteResponse createFavorite(String username, CreateFavoriteRequest request) {
		assertUserExists(username);
		assertSongExists(request.songName());

		if (favoriteRepository.existsByUsernameAndSongName(username, request.songName())) {
			throw new ConflictException("收藏已存在");
		}

		Favorite favorite = new Favorite();
		favorite.setUsername(username);
		favorite.setSongName(request.songName());

		Favorite saved = favoriteRepository.save(favorite);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<FavoriteResponse> listFavoritesByUsername(String username) {
		assertUserExists(username);

		return favoriteRepository.findAllByUsernameOrderByIdDesc(username)
				.stream()
				.map(FavoriteService::toResponse)
				.toList();
	}

	@Transactional
	public void deleteFavorite(Long id) {
		if (!favoriteRepository.existsById(id)) {
			throw new NotFoundException("收藏不存在");
		}
		favoriteRepository.deleteById(id);
	}

	private static FavoriteResponse toResponse(Favorite favorite) {
		return new FavoriteResponse(favorite.getId(), favorite.getUsername(), favorite.getSongName());
	}

	private void assertUserExists(String username) {
		if (!validationEnabled) {
			return;
		}
		userClient.assertUserExists(username);
	}

	private void assertSongExists(String songName) {
		if (!validationEnabled) {
			return;
		}
		metaClient.assertSongExists(songName);
	}
}
