package com.aidigital.reportconstructor.db;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the abandoned-job sweep's bulk update against real Postgres with the real schema, because it is the one
 * statement that decides which in-flight reports get failed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ReportJobAbandonedSweepTest {

	@Container
	@ServiceConnection
	private static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private ReportJobRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void shouldFailOnlyInFlightJobsSilentSinceTheCutoffTest() {
		// Given: a running job silent for two hours, a queued one updated five minutes ago, and an old finished one
		OffsetDateTime now = OffsetDateTime.parse("2026-11-12T12:00:00Z");
		ReportJobEntity abandoned = new ReportJobEntity();
		abandoned.setOwnerUserId("u1");
		abandoned.setStatus("running");
		abandoned.setStep(3);
		abandoned.setTotal(7);
		abandoned.setCreatedAt(now.minusHours(3));
		abandoned.setUpdatedAt(now.minusHours(2));
		ReportJobEntity live = new ReportJobEntity();
		live.setOwnerUserId("u2");
		live.setStatus("queued");
		live.setStep(0);
		live.setTotal(7);
		live.setCreatedAt(now.minusMinutes(6));
		live.setUpdatedAt(now.minusMinutes(5));
		ReportJobEntity finished = new ReportJobEntity();
		finished.setOwnerUserId("u3");
		finished.setStatus("done");
		finished.setStep(7);
		finished.setTotal(7);
		finished.setCreatedAt(now.minusHours(4));
		finished.setUpdatedAt(now.minusHours(2));
		Long abandonedId = entityManager.persistAndFlush(abandoned).getId();
		Long liveId = entityManager.persistAndFlush(live).getId();
		Long finishedId = entityManager.persistAndFlush(finished).getId();

		// When:
		int failed = repository.failInFlightNotUpdatedSince(
				List.of("queued", "running"), now.minusMinutes(60), "error", "interrupted", now);
		entityManager.clear();

		// Then:
		assertThat(failed).isEqualTo(1);
		ReportJobEntity reloaded = repository.findById(abandonedId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo("error");
		assertThat(reloaded.getErrorMessage()).isEqualTo("interrupted");
		assertThat(reloaded.getUpdatedAt()).isEqualTo(now);
		assertThat(repository.findById(liveId).orElseThrow().getStatus()).isEqualTo("queued");
		assertThat(repository.findById(finishedId).orElseThrow().getStatus()).isEqualTo("done");
	}
}
