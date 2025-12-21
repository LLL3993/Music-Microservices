package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", configuration = FeignSupportConfig.class, fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

	@GetMapping("/api/users/username")
	void assertUserExists(@RequestParam("username") String username) throws NotFoundException;
}
