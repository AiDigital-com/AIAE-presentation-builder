package com.aidigital.reportconstructor.service.reports.usage.mappers;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeUsageEventEntityMapperTest {

	private final ClaudeUsageEventEntityMapper mapper = new ClaudeUsageEventEntityMapperImpl();

	@Test
	void shouldCopyScopeCountsAndStatusCodeIntoANewEventTest() {
		// Given:
		ClaudeUsageScope scope = new ClaudeUsageScope(7L, "user-1", "jane@aidigital.com");
		OffsetDateTime at = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);

		// When:
		ClaudeUsageEventEntity event = mapper.toEntity(
				scope, "batch-a", ClaudeUsageStatus.values()[0], 100L, 20L, 3L, 4L, "claude-x", at);

		// Then:
		assertThat(event.getId()).isNull();
		assertThat(event.getJobId()).isEqualTo(7L);
		assertThat(event.getOwnerUserId()).isEqualTo("user-1");
		assertThat(event.getOwnerEmail()).isEqualTo("jane@aidigital.com");
		assertThat(event.getLabel()).isEqualTo("batch-a");
		assertThat(event.getStatus()).isEqualTo(ClaudeUsageStatus.values()[0].getCode());
		assertThat(event.getInputTokens()).isEqualTo(100L);
		assertThat(event.getOutputTokens()).isEqualTo(20L);
		assertThat(event.getCacheWriteTokens()).isEqualTo(3L);
		assertThat(event.getCacheReadTokens()).isEqualTo(4L);
		assertThat(event.getModel()).isEqualTo("claude-x");
		assertThat(event.getCreatedAt()).isEqualTo(at);
	}

	@Test
	void shouldLeaveJobAndOwnerEmptyWhenTheCallBelongsToNoJobTest() {
		// Given:
		OffsetDateTime at = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);

		// When:
		ClaudeUsageEventEntity event = mapper.toEntity(
				null, "batch-a", ClaudeUsageStatus.values()[0], 1L, 1L, 0L, 0L, "claude-x", at);

		// Then:
		assertThat(event.getJobId()).isNull();
		assertThat(event.getOwnerUserId()).isNull();
		assertThat(event.getOwnerEmail()).isNull();
		assertThat(event.getLabel()).isEqualTo("batch-a");
	}
}
