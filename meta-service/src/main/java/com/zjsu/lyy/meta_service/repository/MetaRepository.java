package com.zjsu.lyy.meta_service.repository;

import com.zjsu.lyy.meta_service.entity.Meta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaRepository extends JpaRepository<Meta, Long> {

	Optional<Meta> findBySongName(String songName);

	List<Meta> findBySongNameContainingIgnoreCase(String songName);

	List<Meta> findByArtistContainingIgnoreCase(String artist);

	boolean existsBySongName(String songName);

	boolean existsBySongNameAndIdNot(String songName, Long id);
}
