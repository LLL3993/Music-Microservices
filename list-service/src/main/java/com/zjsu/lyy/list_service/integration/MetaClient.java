package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "meta-service", configuration = FeignSupportConfig.class, fallbackFactory = MetaClientFallbackFactory.class)
public interface MetaClient {

	@GetMapping("/api/meta/song-name")
	void assertSongExists(@RequestParam("songName") String songName) throws NotFoundException;
}
