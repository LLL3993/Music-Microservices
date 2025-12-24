package com.zjsu.lyy.meta_service.service;

import com.zjsu.lyy.meta_service.dto.CreateMetaRequest;
import com.zjsu.lyy.meta_service.dto.MetaResponse;
import com.zjsu.lyy.meta_service.dto.UpdateMetaRequest;
import com.zjsu.lyy.meta_service.entity.Meta;
import com.zjsu.lyy.meta_service.exception.ConflictException;
import com.zjsu.lyy.meta_service.exception.NotFoundException;
import com.zjsu.lyy.meta_service.messaging.MetaEventPublisher;
import com.zjsu.lyy.meta_service.repository.MetaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetaService {

	private final MetaRepository metaRepository;
	private final MetaEventPublisher metaEventPublisher;

	public MetaService(MetaRepository metaRepository, MetaEventPublisher metaEventPublisher) {
		this.metaRepository = metaRepository;
		this.metaEventPublisher = metaEventPublisher;
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

	@Transactional(readOnly = true)
	public List<MetaResponse> getBySongNameLike(String songName) {
		if (songName == null || songName.isBlank()) {
			return List.of();
		}
		return metaRepository.findBySongNameContainingIgnoreCase(songName)
				.stream()
				.map(MetaService::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<MetaResponse> getByArtistNameLike(String artistName) {
		if (artistName == null || artistName.isBlank()) {
			return List.of();
		}
		return metaRepository.findByArtistContainingIgnoreCase(artistName)
				.stream()
				.map(MetaService::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<MetaResponse> listAll() {
		return metaRepository.findAll()
				.stream()
				.map(MetaService::toResponse)
				.toList();
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
		Meta meta = metaRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("歌曲不存在"));
		String songName = meta.getSongName();
		metaRepository.deleteById(id);
		metaEventPublisher.publishSongDeleted(songName);
	}

	private static MetaResponse toResponse(Meta meta) {
		return new MetaResponse(meta.getId(), meta.getSongName(), meta.getArtist());
	}
}
