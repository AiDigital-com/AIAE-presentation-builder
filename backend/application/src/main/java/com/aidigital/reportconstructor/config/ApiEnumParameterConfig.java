package com.aidigital.reportconstructor.config;

import com.aidigital.reportconstructor.api.v1.model.AdminReportSortV1;
import com.aidigital.reportconstructor.api.v1.model.SortDirectionV1;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Binds enum-typed query parameters by their OpenAPI wire value.
 *
 * <p>Spring's default {@code String → Enum} conversion matches the Java constant name ({@code TOKENS},
 * {@code ASC}), while the contract and the frontend send the wire values ({@code tokens}, {@code asc}). Each
 * generated enum's {@code fromValue} is the contract's own mapping, so a documented value binds. When it
 * rejects a value, Spring's request binding still falls back to {@code Enum.valueOf}, so a constant name keeps
 * working and anything else is a 400.
 */
@Configuration
public class ApiEnumParameterConfig implements WebMvcConfigurer {

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(String.class, AdminReportSortV1.class, AdminReportSortV1::fromValue);
		registry.addConverter(String.class, SortDirectionV1.class, SortDirectionV1::fromValue);
	}
}
