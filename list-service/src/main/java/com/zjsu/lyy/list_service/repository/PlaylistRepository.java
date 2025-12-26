package com.zjsu.lyy.list_service.repository;

import com.zjsu.lyy.list_service.entity.Playlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
	Optional<Playlist> findByUsernameAndPlaylistName(String username, String playlistName);

	boolean existsByUsernameAndPlaylistName(String username, String playlistName);

	boolean existsByUsernameAndPlaylistNameAndIdNot(String username, String playlistName, Long id);

	List<Playlist> findAllByUsernameOrderByIdDesc(String username);

	List<Playlist> findAllByPlaylistNameOrderByIdDesc(String playlistName);

	List<Playlist> findAllByIsPublicTrueOrderByIdDesc(Pageable pageable);

	void deleteAllByUsername(String username);
}
