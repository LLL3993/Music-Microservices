package com.zjsu.lyy.meta_service.controller;

import com.zjsu.lyy.meta_service.dto.CreateMetaRequest;
import com.zjsu.lyy.meta_service.dto.MetaResponse;
import com.zjsu.lyy.meta_service.dto.UpdateMetaRequest;
import com.zjsu.lyy.meta_service.service.MetaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

	private final MetaService metaService;

	public MetaController(MetaService metaService) {
		this.metaService = metaService;
	}

	@PostMapping
	public ResponseEntity<MetaResponse> createMeta(@Valid @RequestBody CreateMetaRequest request) {
		MetaResponse created = metaService.createMeta(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/song-name")
	public MetaResponse getBySongName(@RequestParam String songName) {
		return metaService.getBySongName(songName);
	}


	@PutMapping("/{id}")
	public MetaResponse updateMeta(@PathVariable Long id, @Valid @RequestBody UpdateMetaRequest request) {
		return metaService.updateMeta(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteMeta(@PathVariable Long id) {
		metaService.deleteMeta(id);
		return ResponseEntity.noContent().build();
	}
}
