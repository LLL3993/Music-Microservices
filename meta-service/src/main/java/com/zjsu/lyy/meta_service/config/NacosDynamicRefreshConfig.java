package com.zjsu.lyy.meta_service.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ByteArrayResource;

@Configuration
@Profile("docker")
public class NacosDynamicRefreshConfig {

	private static final Logger log = LoggerFactory.getLogger(NacosDynamicRefreshConfig.class);
	private static final String DYNAMIC_SOURCE_NAME = "nacos-dynamic";

	private final NacosConfigManager nacosConfigManager;
	private final ConfigurableEnvironment environment;
	private final ApplicationEventPublisher publisher;
	private final RefreshScope refreshScope;

	private final Map<String, Properties> latestProperties = new ConcurrentHashMap<>();
	private volatile List<ImportTarget> importTargets = List.of();

	public NacosDynamicRefreshConfig(
			NacosConfigManager nacosConfigManager,
			ConfigurableEnvironment environment,
			ApplicationEventPublisher publisher,
			RefreshScope refreshScope
	) {
		this.nacosConfigManager = nacosConfigManager;
		this.environment = environment;
		this.publisher = publisher;
		this.refreshScope = refreshScope;
	}

	@PostConstruct
	public void init() {
		List<ImportTarget> targets = resolveImportTargets();
		this.importTargets = targets;
		if (targets.isEmpty()) {
			log.info("Nacos dynamic refresh disabled: no nacos entries in spring.config.import");
			return;
		}

		ConfigService configService = nacosConfigManager.getConfigService();
		for (ImportTarget target : targets) {
			registerListener(configService, target);
		}

		synchronized (this) {
			rebuildCompositePropertySource();
		}

		log.info("Nacos dynamic refresh enabled: listening {} dataIds", targets.size());
	}

	private void registerListener(ConfigService configService, ImportTarget target) {
		try {
			configService.addListener(target.dataId(), target.group(), new Listener() {
				@Override
				public Executor getExecutor() {
					return null;
				}

				@Override
				public void receiveConfigInfo(String configInfo) {
					handleConfigUpdate(target, configInfo);
				}
			});
			log.info("Registered nacos listener dataId={} group={}", target.dataId(), target.group());
		}
		catch (NacosException e) {
			log.warn("Failed to register nacos listener dataId={} group={}", target.dataId(), target.group(), e);
		}
	}

	private void handleConfigUpdate(ImportTarget target, String configInfo) {
		Properties props = parseYamlToProperties(configInfo);
		latestProperties.put(target.dataId(), props);

		synchronized (this) {
			rebuildCompositePropertySource();
		}

		Set<String> keys = props.stringPropertyNames();
		publisher.publishEvent(new EnvironmentChangeEvent(keys));
		refreshScope.refreshAll();

		log.info("Applied nacos update dataId={} keys={}", target.dataId(), keys);
	}

	private void rebuildCompositePropertySource() {
		List<ImportTarget> targets = this.importTargets;
		if (targets.isEmpty()) {
			return;
		}

		CompositePropertySource composite = new CompositePropertySource(DYNAMIC_SOURCE_NAME);
		List<ImportTarget> sorted = targets.stream()
				.sorted(Comparator.comparingInt(ImportTarget::order).reversed())
				.toList();

		for (ImportTarget target : sorted) {
			Properties props = latestProperties.getOrDefault(target.dataId(), new Properties());
			composite.addPropertySource(new PropertiesPropertySource("nacos-dynamic:" + target.dataId(), props));
		}

		environment.getPropertySources().remove(DYNAMIC_SOURCE_NAME);
		environment.getPropertySources().addFirst(composite);
	}

	private List<ImportTarget> resolveImportTargets() {
		List<String> imports = Binder.get(environment)
				.bind("spring.config.import", Bindable.listOf(String.class))
				.orElse(List.of());

		String defaultGroup = environment.getProperty("spring.cloud.nacos.config.group", "DEFAULT_GROUP");

		int order = 0;
		var targets = new java.util.ArrayList<ImportTarget>();
		for (String entry : imports) {
			if (entry == null) {
				order++;
				continue;
			}
			String trimmed = entry.trim();
			String nacosPart = null;
			if (trimmed.startsWith("optional:nacos:")) {
				nacosPart = trimmed.substring("optional:nacos:".length());
			}
			else if (trimmed.startsWith("nacos:")) {
				nacosPart = trimmed.substring("nacos:".length());
			}
			if (nacosPart == null || nacosPart.isBlank()) {
				order++;
				continue;
			}

			String dataId;
			String query = "";
			int idx = nacosPart.indexOf('?');
			if (idx >= 0) {
				dataId = nacosPart.substring(0, idx);
				query = nacosPart.substring(idx + 1);
			}
			else {
				dataId = nacosPart;
			}

			dataId = dataId.trim();
			if (dataId.isBlank()) {
				order++;
				continue;
			}

			String group = extractQueryParam(query, "group");
			if (group == null || group.isBlank()) {
				group = defaultGroup;
			}

			targets.add(new ImportTarget(dataId, group, order));
			order++;
		}
		return targets;
	}

	private static String extractQueryParam(String query, String key) {
		if (query == null || query.isBlank() || key == null || key.isBlank()) {
			return null;
		}
		String[] parts = query.split("&");
		for (String part : parts) {
			int idx = part.indexOf('=');
			if (idx <= 0) continue;
			String k = part.substring(0, idx).trim();
			if (!key.equals(k)) continue;
			return part.substring(idx + 1).trim();
		}
		return null;
	}

	private static Properties parseYamlToProperties(String yamlContent) {
		YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
		factory.setResources(new ByteArrayResource(yamlContent.getBytes(StandardCharsets.UTF_8)));
		Properties props = factory.getObject();
		return props == null ? new Properties() : props;
	}

	private record ImportTarget(String dataId, String group, int order) {
	}
}
