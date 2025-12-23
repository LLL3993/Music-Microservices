package com.zjsu.lyy.list_service.repository;

import com.zjsu.lyy.list_service.entity.Favorite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
	boolean existsByUsernameAndSongName(String username, String songName);

	List<Favorite> findAllByUsernameOrderByIdDesc(String username);

	void deleteAllByUsername(String username);

	void deleteAllBySongName(String songName);
}
