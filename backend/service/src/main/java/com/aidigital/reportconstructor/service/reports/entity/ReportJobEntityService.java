package com.aidigital.reportconstructor.service.reports.entity;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Paired entity service for {@link ReportJobEntity}.
 * The sole bean allowed to inject {@link ReportJobRepository}.
 */
@Service
@RequiredArgsConstructor
public class ReportJobEntityService {

	private final ReportJobRepository reportJobRepository;

	/**
	 * Persists (insert or update) a report job and returns the managed entity.
	 *
	 * @param entity entity to save
	 * @return saved entity with any generated fields populated
	 */
	public ReportJobEntity save(ReportJobEntity entity) {
		return reportJobRepository.save(entity);
	}

	/**
	 * Looks up a report job by its surrogate id.
	 *
	 * @param id surrogate identifier of the job
	 * @return an {@link Optional} containing the job, or empty when not found
	 */
	public Optional<ReportJobEntity> findById(Long id) {
		return reportJobRepository.findById(id);
	}

	/**
	 * Deletes a report job by its surrogate id; a missing id is a no-op.
	 *
	 * @param id surrogate identifier of the job
	 */
	public void deleteById(Long id) {
		reportJobRepository.deleteById(id);
	}

	/**
	 * Lists a single user's jobs, newest first.
	 *
	 * @param ownerUserId internal owner id whose jobs are returned
	 * @return the owner's jobs ordered by creation time descending
	 */
	public List<ReportJobEntity> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId) {
		return reportJobRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
	}

	/**
	 * Lists every job, newest first.
	 *
	 * @return all jobs ordered by creation time descending
	 */
	public List<ReportJobEntity> findAllByOrderByCreatedAtDesc() {
		return reportJobRepository.findAllByOrderByCreatedAtDesc();
	}

	/**
	 * Counts jobs in any of the given statuses.
	 *
	 * @param statuses status codes to count
	 * @return the number of matching jobs
	 */
	public int countByStatusIn(Collection<String> statuses) {
		return reportJobRepository.countByStatusIn(statuses);
	}

	/**
	 * Lists the most recent jobs in any of the given statuses, bounded by the page.
	 *
	 * @param statuses status codes to include
	 * @param pageable bound on how many are returned
	 * @return the matching jobs, newest first
	 */
	public List<ReportJobEntity> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses, Pageable pageable) {
		return reportJobRepository.findByStatusInOrderByCreatedAtDesc(statuses, pageable);
	}

	/**
	 * Lists every job in any of the given statuses, unbounded.
	 *
	 * @param statuses status codes to include
	 * @return the matching jobs, newest first
	 */
	public List<ReportJobEntity> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses) {
		return reportJobRepository.findByStatusInOrderByCreatedAtDesc(statuses);
	}

	/**
	 * Lists the most recent jobs that completed but recorded generation warnings.
	 *
	 * @param pageable bound on how many are returned
	 * @return the matching jobs, newest first
	 */
	public List<ReportJobEntity> findRecentWarned(Pageable pageable) {
		return reportJobRepository.findRecentWarned(pageable);
	}

	/**
	 * Reports when the earliest job was created.
	 *
	 * @return the earliest creation timestamp, or {@code null} when no job exists
	 */
	public OffsetDateTime earliestCreatedAt() {
		return reportJobRepository.earliestCreatedAt();
	}

	/**
	 * Lists every distinct report owner with the email last recorded for it.
	 *
	 * @return one row per owner
	 */
	public List<JobOwner> listOwners() {
		return reportJobRepository.listOwners();
	}

	/**
	 * Lists deliverable report jobs (everything except the intermediate sheet step) one page at a time.
	 *
	 * @param sheetTarget wire code of the intermediate sheet target to exclude
	 * @param pageable    the page and sort order to apply
	 * @return the requested page, with the unfiltered total attached
	 */
	public Page<ReportJobEntity> findDeliverables(String sheetTarget, Pageable pageable) {
		return reportJobRepository.findDeliverables(sheetTarget, pageable);
	}
}
