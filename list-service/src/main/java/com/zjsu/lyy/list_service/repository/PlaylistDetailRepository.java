package com.zjsu.lyy.list_service.repository;

import com.zjsu.lyy.list_service.entity.PlaylistDetail;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistDetailRepository extends JpaRepository<PlaylistDetail, Long> {
	boolean existsByUsernameAndPlaylistNameAndSongName(String username, String playlistName, String songName);

	List<PlaylistDetail> findAllByUsernameAndPlaylistNameOrderByIdDesc(String username, String playlistName);

	List<PlaylistDetail> findAllByUsernameAndPlaylistName(String username, String playlistName);

	void deleteAllByUsernameAndPlaylistName(String username, String playlistName);
}
