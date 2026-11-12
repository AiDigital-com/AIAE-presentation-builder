package com.aidigital.reportconstructor.service.reports.usage.mappers;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.common.mapping.ServiceMapperConfig;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;

/**
 * Builds {@link ClaudeUsageEventEntity} rows, one per Claude call.
 */
@Mapper(config = ServiceMapperConfig.class)
public interface ClaudeUsageEventEntityMapper {

	/**
	 * Creates the usage event for one Claude call. Job and owner come from the bound scope and stay
	 * {@code null} when the call belongs to no job; the id is assigned on save.
	 *
	 * @param scope            the bound scope, or {@code null}
	 * @param label            batch tag the call was logged under (already defaulted by the caller)
	 * @param status           whether the counts are measured or estimated
	 * @param inputTokens      plain input tokens
	 * @param outputTokens     output tokens
	 * @param cacheWriteTokens prompt-cache write tokens
	 * @param cacheReadTokens  prompt-cache read tokens
	 * @param model            model the call billed against
	 * @param createdAt        when the call was recorded
	 * @return a transient entity ready to save
	 */
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "jobId", source = "scope.jobId")
	@Mapping(target = "ownerUserId", source = "scope.ownerUserId")
	@Mapping(target = "ownerEmail", source = "scope.ownerEmail")
	@Mapping(target = "label", source = "label")
	@Mapping(target = "status", source = "status.code")
	@Mapping(target = "inputTokens", source = "inputTokens")
	@Mapping(target = "outputTokens", source = "outputTokens")
	@Mapping(target = "cacheWriteTokens", source = "cacheWriteTokens")
	@Mapping(target = "cacheReadTokens", source = "cacheReadTokens")
	@Mapping(target = "model", source = "model")
	@Mapping(target = "createdAt", source = "createdAt")
	ClaudeUsageEventEntity toEntity(
			ClaudeUsageScope scope, String label, ClaudeUsageStatus status,
			long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens,
			String model, OffsetDateTime createdAt);
}
