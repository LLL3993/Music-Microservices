package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignSupportConfig {

	@Bean
	public ErrorDecoder errorDecoder() {
		return new ErrorDecoder() {
			private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

			@Override
			public Exception decode(String methodKey, Response response) {
				if (response.status() == 404) {
					if (methodKey.contains("UserClient#assertUserExists")) {
						return new NotFoundException("用户不存在");
					}
					if (methodKey.contains("MetaClient#assertSongExists")) {
						return new NotFoundException("歌曲不存在");
					}
				}
				return defaultDecoder.decode(methodKey, response);
			}
		};
	}
}
