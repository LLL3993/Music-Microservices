package com.zjsu.lyy.meta_service.service;

import com.zjsu.lyy.meta_service.dto.CreateMetaRequest;
import com.zjsu.lyy.meta_service.dto.MetaResponse;
import com.zjsu.lyy.meta_service.dto.UpdateMetaRequest;
import com.zjsu.lyy.meta_service.entity.Meta;
import com.zjsu.lyy.meta_service.exception.ConflictException;
import com.zjsu.lyy.meta_service.exception.NotFoundException;
import com.zjsu.lyy.meta_service.repository.MetaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetaService {

	private final MetaRepository metaRepository;

	public MetaService(MetaRepository metaRepository) {
		this.metaRepository = metaRepository;
	}

	@Transactional
	public MetaResponse createMeta(CreateMetaRequest request) {
		if (metaRepository.existsBySongName(request.songName())) {
			throw new ConflictException("songName 已存在");
		}

		Meta meta = new Meta();
		meta.setSongName(request.songName());
		meta.setArtist(request.artist());

		Meta saved = metaRepository.save(meta);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public MetaResponse getBySongName(String songName) {
		Meta meta = metaRepository.findBySongName(songName)
				.orElseThrow(() -> new NotFoundException("歌曲不存在"));
		return toResponse(meta);
	}

	@Transactional
	public MetaResponse updateMeta(Long id, UpdateMetaRequest request) {
		Meta meta = metaRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("歌曲不存在"));

		if (metaRepository.existsBySongNameAndIdNot(request.songName(), id)) {
			throw new ConflictException("songName 已存在");
		}

		meta.setSongName(request.songName());
		meta.setArtist(request.artist());

		Meta saved = metaRepository.save(meta);
		return toResponse(saved);
	}

	@Transactional
	public void deleteMeta(Long id) {
		if (!metaRepository.existsById(id)) {
			throw new NotFoundException("歌曲不存在");
		}
		metaRepository.deleteById(id);
	}

	private static MetaResponse toResponse(Meta meta) {
		return new MetaResponse(meta.getId(), meta.getSongName(), meta.getArtist());
	}
}
