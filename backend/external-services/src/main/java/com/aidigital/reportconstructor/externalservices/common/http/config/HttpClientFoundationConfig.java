package com.aidigital.reportconstructor.externalservices.common.http.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link PooledHttpClientProperties} binding. {@code PooledRestClientFactory} is a component and
 * is picked up by the application's component scan.
 */
@Configuration
@EnableConfigurationProperties(PooledHttpClientProperties.class)
public class HttpClientFoundationConfig {
}
