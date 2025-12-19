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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final UserClient userClient;
	private final MetaClient metaClient;

	public FavoriteService(FavoriteRepository favoriteRepository, UserClient userClient, MetaClient metaClient) {
		this.favoriteRepository = favoriteRepository;
		this.userClient = userClient;
		this.metaClient = metaClient;
	}

	@Transactional
	public FavoriteResponse createFavorite(String username, CreateFavoriteRequest request) {
		userClient.assertUserExists(username);
		metaClient.assertSongExists(request.songName());

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
		userClient.assertUserExists(username);

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
}
