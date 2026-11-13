package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminDateRangeResolver;
import com.aidigital.reportconstructor.service.admin.AdminStatsAssembler;
import com.aidigital.reportconstructor.service.admin.UsageRollupRefresher;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
		verifyNoInteractions(rollupRefresher, assembler);
	}

	@Test
	void shouldRefreshTheRollupThenAssembleTheResolvedWindowTest() {
		// Given:
		LocalDate today = LocalDate.of(2026, 11, 13);
		AdminDateRange range = new AdminDateRange(today.minusDays(29), today, AdminPeriodUnit.WEEK);
		AdminStats assembled = mock(AdminStats.class);
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		when(currentTime.nowLocalDate()).thenReturn(today);
		when(rangeResolver.resolve(null, null, today)).thenReturn(range);
		when(assembler.assemble(range)).thenReturn(assembled);

		// When:
		AdminStats stats = service.statsFor("admin@aidigital.com", null, null);

		// Then: the figures are read after the rollup is brought up to date
		assertThat(stats).isSameAs(assembled);
		verify(rollupRefresher).ensureFresh();
	}

	@Test
	void shouldServeTheFiguresAsTheyStandWhenTheRollupRefreshFailsTest() {
		// Given: refreshing throws, which must not reach the caller
		LocalDate today = LocalDate.of(2026, 11, 13);
		AdminDateRange range = new AdminDateRange(today.minusDays(6), today, AdminPeriodUnit.DAY);
		AdminStats assembled = mock(AdminStats.class);
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		doThrow(new IllegalStateException("db down")).when(rollupRefresher).ensureFresh();
		when(currentTime.nowLocalDate()).thenReturn(today);
		when(rangeResolver.resolve(null, null, today)).thenReturn(range);
		when(assembler.assemble(range)).thenReturn(assembled);

		// When:
		AdminStats stats = service.statsFor("admin@aidigital.com", null, null);

		// Then:
		assertThat(stats).isSameAs(assembled);
	}
}
