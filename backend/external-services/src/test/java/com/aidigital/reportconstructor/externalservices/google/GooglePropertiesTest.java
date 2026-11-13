package com.aidigital.reportconstructor.externalservices.google;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GooglePropertiesTest {

	@Test
	void shouldDefaultEveryOptionalSettingToOffTest() {
		// Given: properties bound from an environment that configures nothing
		GoogleProperties props = new GoogleProperties();

		// When-Then: every id is blank and every map/list empty, so each feature is a safe no-op
		assertThat(props.getServiceAccountJson()).isEmpty();
		assertThat(props.getSlidesTemplateId()).isEmpty();
		assertThat(props.getSlidesTargetFolderId()).isEmpty();
		assertThat(props.getSummaryTableObjectIds()).isEmpty();
		assertThat(props.getSummarySlideObjectIds()).isEmpty();
		assertThat(props.getResultsSlideObjectIds()).isEmpty();
		assertThat(props.getTacticSlideObjectIds()).isEmpty();
		assertThat(props.getTacticMasterSlideObjectId()).isEmpty();
		assertThat(props.getBreakdownMasterSlideObjectIds()).isEmpty();
		assertThat(props.getThoughtsMasterSlideObjectId()).isEmpty();
		assertThat(props.getSheetsTemplateId()).isEmpty();
		assertThat(props.getEomSheetsTemplateId()).isEmpty();
		assertThat(props.getSheetsTargetFolderId()).isEmpty();
		assertThat(props.getShareWithEmails()).isEmpty();
	}

	@Test
	void shouldNormalizeAnUnsetValueToTheOffDefaultTest() {
		// Given: properties first configured, then given nulls (an env var bound to nothing)
		GoogleProperties props = new GoogleProperties();
		props.setSummaryTableObjectIds(Map.of(1, "tbl"));
		props.setTacticMasterSlideObjectId("master");

		// When:
		props.setSummaryTableObjectIds(null);
		props.setSummarySlideObjectIds(null);
		props.setResultsSlideObjectIds(null);
		props.setTacticSlideObjectIds(null);
		props.setTacticMasterSlideObjectId(null);
		props.setBreakdownMasterSlideObjectIds(null);
		props.setThoughtsMasterSlideObjectId(null);
		props.setEomSheetsTemplateId(null);
		props.setShareWithEmails(null);

		// Then: callers can test for blank/empty without a null check
		assertThat(props.getSummaryTableObjectIds()).isNotNull().isEmpty();
		assertThat(props.getSummarySlideObjectIds()).isNotNull().isEmpty();
		assertThat(props.getResultsSlideObjectIds()).isNotNull().isEmpty();
		assertThat(props.getTacticSlideObjectIds()).isNotNull().isEmpty();
		assertThat(props.getTacticMasterSlideObjectId()).isEmpty();
		assertThat(props.getBreakdownMasterSlideObjectIds()).isNotNull().isEmpty();
		assertThat(props.getThoughtsMasterSlideObjectId()).isEmpty();
		assertThat(props.getEomSheetsTemplateId()).isEmpty();
		assertThat(props.getShareWithEmails()).isNotNull().isEmpty();
	}

	@Test
	void shouldKeepEveryConfiguredValueTest() {
		// Given:
		GoogleProperties props = new GoogleProperties();

		// When:
		props.setServiceAccountJson("{\"type\":\"service_account\"}");
		props.setSlidesTemplateId("deck-template");
		props.setSlidesTargetFolderId("deck-folder");
		props.setSummaryTableObjectIds(Map.of(1, "summary-table-1"));
		props.setSummarySlideObjectIds(Map.of(2, "summary-slide-2"));
		props.setResultsSlideObjectIds(Map.of(3, "results-slide-3"));
		props.setTacticSlideObjectIds(Map.of(4, "tactic-slide-4"));
		props.setTacticMasterSlideObjectId("tactic-master");
		props.setBreakdownMasterSlideObjectIds(Map.of("geo", "geo-master"));
		props.setThoughtsMasterSlideObjectId("thoughts-master");
		props.setSheetsTemplateId("sheet-template");
		props.setEomSheetsTemplateId("eom-sheet-template");
		props.setSheetsTargetFolderId("sheet-folder");
		props.setShareWithEmails(List.of("owner@example.com"));

		// Then:
		assertThat(props.getServiceAccountJson()).isEqualTo("{\"type\":\"service_account\"}");
		assertThat(props.getSlidesTemplateId()).isEqualTo("deck-template");
		assertThat(props.getSlidesTargetFolderId()).isEqualTo("deck-folder");
		assertThat(props.getSummaryTableObjectIds()).containsExactlyEntriesOf(Map.of(1, "summary-table-1"));
		assertThat(props.getSummarySlideObjectIds()).containsExactlyEntriesOf(Map.of(2, "summary-slide-2"));
		assertThat(props.getResultsSlideObjectIds()).containsExactlyEntriesOf(Map.of(3, "results-slide-3"));
		assertThat(props.getTacticSlideObjectIds()).containsExactlyEntriesOf(Map.of(4, "tactic-slide-4"));
		assertThat(props.getTacticMasterSlideObjectId()).isEqualTo("tactic-master");
		assertThat(props.getBreakdownMasterSlideObjectIds()).containsExactlyEntriesOf(Map.of("geo", "geo-master"));
		assertThat(props.getThoughtsMasterSlideObjectId()).isEqualTo("thoughts-master");
		assertThat(props.getSheetsTemplateId()).isEqualTo("sheet-template");
		assertThat(props.getEomSheetsTemplateId()).isEqualTo("eom-sheet-template");
		assertThat(props.getSheetsTargetFolderId()).isEqualTo("sheet-folder");
		assertThat(props.getShareWithEmails()).containsExactly("owner@example.com");
	}
}
