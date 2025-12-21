package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class MetaClientFallbackFactory implements FallbackFactory<MetaClient> {

	@Override
	public MetaClient create(Throwable cause) {
		return songName -> {
			if (cause instanceof NotFoundException notFound) {
				throw notFound;
			}
			if (cause instanceof FeignException.NotFound notFound) {
				throw notFound;
			}
			if (cause instanceof CallNotPermittedException) {
				return;
			}
			throw new IllegalStateException("meta-service 调用失败", cause);
		};
	}
}
