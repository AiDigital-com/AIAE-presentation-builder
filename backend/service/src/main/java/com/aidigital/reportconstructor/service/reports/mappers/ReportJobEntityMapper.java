package com.aidigital.reportconstructor.service.reports.mappers;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.mapping.ServiceMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;

/**
 * Builds {@link ReportJobEntity} rows for newly enqueued report jobs.
 */
@Mapper(config = ServiceMapperConfig.class)
public interface ReportJobEntityMapper {

	/** Wire code of a job that has been enqueued but not started. */
	String QUEUED_STATUS = "queued";

	/** Progress label shown while a job waits for a worker. */
	String QUEUED_LABEL = "Queued…";

	/** Step counter of a job that has not started. */
	String FIRST_STEP = "0";

	/** Number of progress steps a report job reports. */
	String TOTAL_STEPS = "7";

	/**
	 * Creates a queued job owned by the given user. Everything else (context, results, token usage) is
	 * filled in as the job runs; the id is assigned on save.
	 *
	 * @param ownerUserId    Clerk user id of the job's owner
	 * @param reportTypeCode report type requested
	 * @param now            creation time, used for both created and updated timestamps
	 * @return a transient entity ready to save
	 */
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "ownerUserId", source = "ownerUserId")
	@Mapping(target = "reportTypeCode", source = "reportTypeCode")
	@Mapping(target = "status", constant = QUEUED_STATUS)
	@Mapping(target = "step", constant = FIRST_STEP)
	@Mapping(target = "total", constant = TOTAL_STEPS)
	@Mapping(target = "label", constant = QUEUED_LABEL)
	@Mapping(target = "createdAt", source = "now")
	@Mapping(target = "updatedAt", source = "now")
	ReportJobEntity toQueuedJob(String ownerUserId, String reportTypeCode, OffsetDateTime now);
}
