package com.zjsu.lyy.meta_service.repository;

import com.zjsu.lyy.meta_service.entity.Meta;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaRepository extends JpaRepository<Meta, Long> {

	Optional<Meta> findByIdAndDeletedFalse(Long id);

	Optional<Meta> findBySongNameAndDeletedFalse(String songName);

	boolean existsBySongNameAndDeletedFalse(String songName);

	boolean existsBySongNameAndDeletedFalseAndIdNot(String songName, Long id);
}
