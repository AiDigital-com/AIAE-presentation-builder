package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminDateRangeResolver;
import com.aidigital.reportconstructor.service.admin.AdminStatsAssembler;
import com.aidigital.reportconstructor.service.admin.AdminStatsCache;
import com.aidigital.reportconstructor.service.admin.UsageRollupRefresher;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.time.CurrentTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Default {@link AdminStatsService}.
 *
 * <p>Validates admin access, makes sure the {@code usage_daily} rollup is current, then serves the
 * dashboard payload for the resolved window from the snapshot cache, assembling it with
 * {@link AdminStatsAssembler} on a miss.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

	private final AdminAccessPolicy adminAccessPolicy;
	private final UsageRollupRefresher rollupRefresher;
	private final AdminDateRangeResolver rangeResolver;
	private final AdminStatsCache statsCache;
	private final AdminStatsAssembler assembler;
	private final CurrentTime currentTime;

	@Override
	public AdminStats statsFor(String callerEmail, LocalDate from, LocalDate to) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
		if (refreshRollup()) {
			statsCache.invalidate();
		}
		AdminDateRange range = rangeResolver.resolve(from, to, currentTime.nowLocalDate());
		return statsCache.snapshot(range, () -> assembler.assemble(range));
	}

	/**
	 * Brings the rollup up to date before the figures are read, and reports whether it moved.
	 *
	 * <p>A report that finished a minute ago must be visible, and the refresh timer alone would leave
	 * it out until its next tick. But refreshing a cache is not what the caller asked for: the numbers
	 * behind it are still intact in {@code report_jobs}, so a refresh that fails must degrade to
	 * slightly stale figures rather than to an error page. The refresher already swallows its own
	 * failures; this is the outer guarantee, so that nothing escaping it can turn a working dashboard
	 * into a 500.
	 *
	 * @return true when the rollup was rebuilt, so anything derived from it is now stale
	 */
	boolean refreshRollup() {
		try {
			return rollupRefresher.ensureFresh();
		} catch (Exception ex) {
			log.warn("[admin] usage rollup could not be refreshed; serving the figures as they stand", ex);
			return false;
		}
	}
}
