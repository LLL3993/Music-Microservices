package com.zjsu.lyy.meta_service.repository;

import com.zjsu.lyy.meta_service.entity.Meta;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaRepository extends JpaRepository<Meta, Long> {

	Optional<Meta> findBySongName(String songName);

	boolean existsBySongName(String songName);

	boolean existsBySongNameAndIdNot(String songName, Long id);
}
