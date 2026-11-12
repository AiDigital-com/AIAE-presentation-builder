package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminDateRangeResolver;
import com.aidigital.reportconstructor.service.admin.AdminStatsAssembler;
import com.aidigital.reportconstructor.service.admin.AdminStatsCache;
import com.aidigital.reportconstructor.service.admin.UsageRollupRefresher;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.dto.AdminStatsKey;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.time.CurrentTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceImplTest {

	@Mock
	private AdminAccessPolicy adminAccessPolicy;
	@Mock
	private UsageRollupRefresher rollupRefresher;
	@Mock
	private AdminDateRangeResolver rangeResolver;
	@Mock
	private AdminStatsCache statsCache;
	@Mock
	private AdminStatsAssembler assembler;
	@Mock
	private CurrentTime currentTime;

	@InjectMocks
	private AdminStatsServiceImpl service;

	@Test
	void shouldRejectNonAdminBeforeTouchingTheRollupTest() {
		// Given:
		when(adminAccessPolicy.isAdmin("user@aidigital.com")).thenReturn(false);

		// When:
		Throwable thrown = catchThrowable(() -> service.statsFor("user@aidigital.com", null, null));

		// Then:
		assertThat(thrown).isInstanceOf(AppException.class);
		assertThat(((AppException) thrown).getCode()).isEqualTo(ErrorReason.C004.getCode());
		verifyNoInteractions(rollupRefresher, statsCache, assembler);
	}

	@Test
	void shouldDropTheCachedSnapshotWhenTheRollupWasRebuiltAndAssembleOnMissTest() {
		// Given: the rollup moved, and the cache has nothing for the window
		LocalDate today = LocalDate.of(2026, 11, 12);
		AdminDateRange range = new AdminDateRange(today.minusDays(29), today, AdminPeriodUnit.WEEK);
		AdminStats assembled = mock(AdminStats.class);
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		when(rollupRefresher.ensureFresh()).thenReturn(true);
		when(currentTime.nowLocalDate()).thenReturn(today);
		when(rangeResolver.resolve(null, null, today)).thenReturn(range);
		when(assembler.assemble(range)).thenReturn(assembled);
		OffsetDateTime version = OffsetDateTime.parse("2026-11-12T10:15:00Z");
		when(rollupRefresher.lastRefreshedAt()).thenReturn(version);
		when(statsCache.snapshot(eq(new AdminStatsKey(range, version)),
				org.mockito.ArgumentMatchers.<Supplier<AdminStats>>any()))
				.thenAnswer(invocation -> invocation.<Supplier<AdminStats>>getArgument(1).get());

		// When:
		AdminStats stats = service.statsFor("admin@aidigital.com", null, null);

		// Then: the snapshot is keyed by the rollup version, so a rebuild on another node moves it too
		assertThat(stats).isSameAs(assembled);
		verify(statsCache).invalidate();
	}

	@Test
	void shouldServeStaleFiguresWhenTheRollupRefreshFailsTest() {
		// Given: refreshing throws, which must not reach the caller; the version is unknown
		LocalDate today = LocalDate.of(2026, 11, 12);
		AdminDateRange range = new AdminDateRange(today.minusDays(6), today, AdminPeriodUnit.DAY);
		AdminStats cached = mock(AdminStats.class);
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		when(rollupRefresher.ensureFresh()).thenThrow(new IllegalStateException("db down"));
		when(currentTime.nowLocalDate()).thenReturn(today);
		when(rangeResolver.resolve(null, null, today)).thenReturn(range);
		when(statsCache.snapshot(eq(new AdminStatsKey(range, null)),
				org.mockito.ArgumentMatchers.<Supplier<AdminStats>>any()))
				.thenReturn(cached);

		// When:
		AdminStats stats = service.statsFor("admin@aidigital.com", null, null);

		// Then:
		assertThat(stats).isSameAs(cached);
		verify(statsCache, never()).invalidate();
	}
}
