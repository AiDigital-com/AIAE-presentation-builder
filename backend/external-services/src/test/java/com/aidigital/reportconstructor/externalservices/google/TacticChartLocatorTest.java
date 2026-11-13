package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.SheetsChart;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TacticChartLocatorTest {

	@Test
	void chartsBySource_shouldIndexEachLinkedChartByTheWorkbookItPointsAtTest() {
		// Given: a duplicated tactic slide carrying the three linked placeholder charts plus a plain shape
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Page slide = new Page().setObjectId("tct_3").setPageElements(List.of(
				chartElement("el_daily", "book_daily", 100.0),
				chartElement("el_monthly", "book_monthly", 200.0),
				chartElement("el_dist", "book_dist", 300.0),
				new PageElement().setObjectId("el_text")));

		// When:
		Map<String, ChartElementRef> bySource = locator.chartsBySource(slide);

		// Then: every chart is reachable by its source workbook id — the only key both the master and the
		// chart step know — and its geometry is captured so the replacement lands in the same spot
		assertThat(bySource.keySet()).containsExactly("book_daily", "book_monthly", "book_dist");
		assertThat(bySource.get("book_monthly").objectId()).isEqualTo("el_monthly");
		assertThat(bySource.get("book_monthly").transform().slideId()).isEqualTo("tct_3");
		assertThat(bySource.get("book_monthly").transform().transform().getTranslateX()).isEqualTo(200.0);
	}

	@Test
	void chartsBySource_shouldKeepTheFirstOfTwoChartsLinkedToTheSameWorkbookTest() {
		// Given: a slide whose two charts point at the same workbook — an ambiguous master, not a crash
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Page slide = new Page().setObjectId("tct_1").setPageElements(List.of(
				chartElement("el_first", "book_shared", 10.0),
				chartElement("el_second", "book_shared", 20.0)));

		// When-Then: the first one wins, so the pass still renders one chart instead of failing the tactic
		assertThat(locator.chartsBySource(slide).get("book_shared").objectId()).isEqualTo("el_first");
	}

	@Test
	void indexSlide_shouldMergeTheChartsOneEomTacticCarriesAcrossItsMasterCopiesTest() {
		// Given: one EOM tactic's two copies — the first carries the pacing chart, the second the pie
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Map<Integer, Map<String, ChartElementRef>> byTactic = new LinkedHashMap<>();

		// When:
		locator.indexSlide(byTactic, new Page().setObjectId("eom_m0_t1")
				.setPageElements(List.of(chartElement("el_daily", "book_daily", 10.0))), 1);
		locator.indexSlide(byTactic, new Page().setObjectId("eom_m1_t1")
				.setPageElements(List.of(chartElement("el_dist", "book_dist", 20.0))), 1);

		// Then: the tactic ends up with both, so neither chart pass has to guess which copy to look on
		assertThat(byTactic.get(1).keySet()).containsExactly("book_daily", "book_dist");
		assertThat(byTactic.get(1).get("book_dist").objectId()).isEqualTo("el_dist");
	}

	@Test
	void indexSlide_shouldKeepAnEntryForATacticCopyThatCarriesNoChartTest() {
		// Given: a tactic copy drawn without any chart on it — the EOM channel slide
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Map<Integer, Map<String, ChartElementRef>> byTactic = new LinkedHashMap<>();

		// When:
		locator.indexSlide(byTactic, new Page().setObjectId("eom_m0_t1")
				.setPageElements(List.of(new PageElement().setObjectId("el_text"))), 1);

		// Then: the tactic is still listed, empty. An empty overall map means "legacy deck, use the
		// configured slot ids", and on this deck those ids name objects that do not exist — the 400 that
		// wiped out every chart of every tactic
		assertThat(byTactic).containsOnlyKeys(1);
		assertThat(byTactic.get(1)).isEmpty();
	}

	@Test
	void tacticNumberOf_shouldRecognizeAnEomTacticCopyOfAnyMasterTest() {
		// Given: an EOM deck, whose tactic copies are named per master ordinal rather than tct_<n>
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When-Then: every master's copy resolves to its tactic, and the same range check applies — without
		// this the scan finds nothing on an EOM deck and the chart step falls back to the EOC template's
		// configured object ids, which do not exist there
		assertThat(locator.tacticNumberOf("eom_m0_t2", 3)).isEqualTo(2);
		assertThat(locator.tacticNumberOf("eom_m1_t2", 3)).isEqualTo(2);
		assertThat(locator.tacticNumberOf("eom_m1_t4", 3)).isNull();
		assertThat(locator.tacticNumberOf("eom_m1_tx", 3)).isNull();
	}

	@Test
	void tacticNumberOf_shouldRecognizeOnlyActiveTacticSlideIdsTest() {
		// Given: a locator and a deck naming convention of tct_<n>
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When-Then: a copy within the active range resolves; a breakdown copy, a template slide, a
		// tactic above the active count and a null id do not
		assertThat(locator.tacticNumberOf("tct_2", 3)).isEqualTo(2);
		assertThat(locator.tacticNumberOf("tct_4", 3)).isNull();
		assertThat(locator.tacticNumberOf("bd_dev_2", 3)).isNull();
		assertThat(locator.tacticNumberOf("p7", 3)).isNull();
		assertThat(locator.tacticNumberOf(null, 3)).isNull();
	}

	@Test
	void load_shouldIndexTheChartsOfEveryActiveTacticCopyInTheDeckTest() throws Exception {
		// Given: a deck holding a template slide, tactic 1's copy with a linked chart, tactic 2's copy read
		// without elements, and a copy for a tactic above the active count
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), googleCalls);
		Slides slides = mock(Slides.class);
		Slides.Presentations presentations = mock(Slides.Presentations.class);
		Slides.Presentations.Get get = mock(Slides.Presentations.Get.class);
		when(slides.presentations()).thenReturn(presentations);
		when(presentations.get("deck-1")).thenReturn(get);
		when(get.setFields("slides.objectId,slides.pageElements(objectId,size,transform,sheetsChart)"))
				.thenReturn(get);
		PageElement daily = new PageElement().setObjectId("el_daily").setSize(new Size())
				.setTransform(new AffineTransform().setTranslateX(5.0))
				.setSheetsChart(new SheetsChart().setSpreadsheetId("book_daily").setChartId(1));
		Presentation deck = new Presentation().setSlides(List.of(
				new Page().setObjectId("p1"),
				new Page().setObjectId("tct_1").setPageElements(List.of(daily)),
				new Page().setObjectId("tct_2"),
				new Page().setObjectId("tct_3").setPageElements(List.of(daily))));
		when(googleCalls.execute(get)).thenReturn(deck);
		List<String> errors = new ArrayList<>();

		// When:
		Map<Integer, Map<String, ChartElementRef>> byTactic = locator.load(slides, "deck-1", 2, errors);

		// Then: both active tactics are listed — tactic 2 empty — and the inactive copy is ignored
		assertThat(errors).isEmpty();
		assertThat(byTactic).containsOnlyKeys(1, 2);
		assertThat(byTactic.get(1).get("book_daily").objectId()).isEqualTo("el_daily");
		assertThat(byTactic.get(1).get("book_daily").transform().slideId()).isEqualTo("tct_1");
		assertThat(byTactic.get(2)).isEmpty();
	}

	@Test
	void load_shouldReturnNothingForADeckWithoutSlidesTest() throws Exception {
		// Given: a deck read back with no slides at all
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), googleCalls);
		Slides slides = mock(Slides.class);
		Slides.Presentations presentations = mock(Slides.Presentations.class);
		Slides.Presentations.Get get = mock(Slides.Presentations.Get.class);
		when(slides.presentations()).thenReturn(presentations);
		when(presentations.get("deck-1")).thenReturn(get);
		when(get.setFields("slides.objectId,slides.pageElements(objectId,size,transform,sheetsChart)"))
				.thenReturn(get);
		when(googleCalls.execute(get)).thenReturn(new Presentation());
		List<String> errors = new ArrayList<>();

		// When:
		Map<Integer, Map<String, ChartElementRef>> byTactic = locator.load(slides, "deck-1", 2, errors);

		// Then: the caller falls back to the configured slot ids, and nothing is reported
		assertThat(byTactic).isEmpty();
		assertThat(errors).isEmpty();
	}

	@Test
	void load_shouldReportADeckThatCannotBeReadTest() throws Exception {
		// Given: a Slides read that fails
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), googleCalls);
		Slides slides = mock(Slides.class);
		Slides.Presentations presentations = mock(Slides.Presentations.class);
		Slides.Presentations.Get get = mock(Slides.Presentations.Get.class);
		when(slides.presentations()).thenReturn(presentations);
		when(presentations.get("deck-1")).thenReturn(get);
		when(get.setFields("slides.objectId,slides.pageElements(objectId,size,transform,sheetsChart)"))
				.thenReturn(get);
		when(googleCalls.execute(get)).thenThrow(new IOException("quota exceeded"));
		List<String> errors = new ArrayList<>();

		// When:
		Map<Integer, Map<String, ChartElementRef>> byTactic = locator.load(slides, "deck-1", 2, errors);

		// Then:
		assertThat(byTactic).isEmpty();
		assertThat(errors).containsExactly("Tactic charts: could not read presentation layout — quota exceeded");
	}

	@Test
	void chartsBySource_shouldSkipChartsWithoutASourceWorkbookOrObjectIdTest() {
		// Given: a chart with no source workbook and a linked chart with no object id next to a valid one
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), mock(GoogleCallTimer.class));
		Page slide = new Page().setObjectId("tct_1").setPageElements(List.of(
				new PageElement().setObjectId("el_unlinked").setSheetsChart(new SheetsChart()),
				new PageElement().setSheetsChart(new SheetsChart().setSpreadsheetId("book_orphan")),
				new PageElement().setObjectId("el_dist").setSize(new Size()).setTransform(new AffineTransform())
						.setSheetsChart(new SheetsChart().setSpreadsheetId("book_dist"))));

		// When:
		Map<String, ChartElementRef> bySource = locator.chartsBySource(slide);

		// Then:
		assertThat(bySource).containsOnlyKeys("book_dist");
		assertThat(bySource.get("book_dist").objectId()).isEqualTo("el_dist");
	}

	@Test
	void tacticNumberOf_shouldRejectAnEomCopyNumberedBelowOneTest() {
		// Given:
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming(), mock(GoogleCallTimer.class));

		// When-Then:
		assertThat(locator.tacticNumberOf("eom_m0_t0", 3)).isNull();
	}

	private PageElement chartElement(String objectId, String spreadsheetId, double translateX) {
		return new PageElement()
				.setObjectId(objectId)
				.setSize(new Size())
				.setTransform(new AffineTransform().setTranslateX(translateX))
				.setSheetsChart(new SheetsChart().setSpreadsheetId(spreadsheetId).setChartId(1));
	}
}
