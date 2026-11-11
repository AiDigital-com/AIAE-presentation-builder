package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownChartSeries;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartJob;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartSlice;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.SheetsChart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Links the per-tactic audience/device breakdown charts onto the duplicated breakdown slides. Each
 * breakdown master slide carries an embedded chart linked to a shared source workbook; duplicating the
 * master copies that chart still pointing at the shared workbook, so every copy would render the same
 * empty data. This builder gives each copy its own data: it copies the section's source workbook, writes
 * the tactic's impressions into it (share of voice stays a live formula), and swaps the copy's chart for
 * a fresh chart linked to the new workbook.
 *
 * <p>Pure orchestration over the injected Google helper beans, mirroring {@link TacticChartBuilder}: it
 * holds no Google clients itself — those arrive per request as a {@link ChartClients} bundle — and
 * collects per-chart failures rather than aborting the rest.
 */
@Slf4j
@Component
public class BreakdownChartBuilder {

	private final DriveCopier driveCopier;
	private final ChartSheetWriter chartSheetWriter;
	private final ChartSpecBuilder chartSpecBuilder;
	private final SlideChartSwapper slideChartSwapper;
	private final ChartErrorTranslator chartErrors;
	private final BreakdownChartCatalog catalog;
	private final BreakdownSlideNaming naming;
	private final ChartFileSharer chartFileSharer;
	private final GoogleCallTimer googleCalls;

	/**
	 * Creates the builder over its Drive, Sheets and Slides collaborators.
	 *
	 * @param driveCopier       copies chart-template workbooks
	 * @param chartSheetWriter  writes breakdown values into the copied workbooks
	 * @param chartSpecBuilder  reads and restyles the workbook chart specs
	 * @param slideChartSwapper swaps placeholder charts for linked Sheets charts
	 * @param chartErrors       turns API failures into readable per-chart errors
	 * @param catalog           breakdown chart template catalog
	 * @param naming            reads tactic numbers from duplicated slide ids
	 * @param chartFileSharer   shares loose chart workbooks
	 * @param googleCalls       times every direct Sheets/Slides call on the shared external-call timer
	 */
	public BreakdownChartBuilder(
			DriveCopier driveCopier,
			ChartSheetWriter chartSheetWriter,
			ChartSpecBuilder chartSpecBuilder,
			SlideChartSwapper slideChartSwapper,
			ChartErrorTranslator chartErrors,
			BreakdownChartCatalog catalog,
			BreakdownSlideNaming naming,
			ChartFileSharer chartFileSharer,
			GoogleCallTimer googleCalls) {
		this.googleCalls = googleCalls;
		this.driveCopier = driveCopier;
		this.chartSheetWriter = chartSheetWriter;
		this.chartSpecBuilder = chartSpecBuilder;
		this.slideChartSwapper = slideChartSwapper;
		this.chartErrors = chartErrors;
		this.catalog = catalog;
		this.naming = naming;
		this.chartFileSharer = chartFileSharer;
	}

	/**
	 * Builds and links every requested breakdown chart, returning per-chart error strings.
	 *
	 * @param clients the Drive/Sheets/Slides clients to use for this request
	 * @param req     the breakdown chart request describing the deck and per-tactic slices
	 * @return human-readable error strings for any per-chart failures (empty on full success)
	 */
	List<String> buildBreakdownCharts(ChartClients clients, BreakdownChartRequest req) {
		List<String> errors = new ArrayList<>();
		if (req.jobs() == null || req.jobs().isEmpty()) {
			return errors;
		}
		Map<String, Map<String, ChartElementRef>> chartsBySlide =
				loadBreakdownChartElements(clients.slides(), req.presentationId(), errors);

		String folderId = null;
		try {
			folderId = driveCopier.createFolder(clients.drive(), "Breakdown charts — " + req.campaignTitle());
		} catch (IOException ex) {
			log.warn("[breakdown-charts] could not create folder, copies go to root: {}", ex.getMessage());
		}
		chartFileSharer.shareFolder(clients.drive(), folderId);

		for (BreakdownChartJob job : req.jobs()) {
			try {
				renderBreakdownChart(clients, req, job, chartsBySlide, folderId, errors);
			} catch (IOException | RuntimeException ex) {
				errors.add(chartErrors.describeChartError(
						"Breakdown " + job.seriesCode() + " tactic " + job.tacticNum(), ex));
			}
		}
		return errors;
	}

	/**
	 * Renders one breakdown chart: resolves its section config and target chart element, copies the source
	 * workbook, writes the tactic's impressions and swaps the slide chart. Missing config, an absent chart
	 * element is recorded as an error rather than thrown; a job with no positive slice removes the slide's sample
	 * chart, so a tactic without data does not show the master's sample bars.
	 *
	 * @param clients       the Google clients for this request
	 * @param req           the breakdown chart request (source of the deck id and copy names)
	 * @param job           the breakdown chart inputs
	 * @param chartsBySlide breakdown slide object id &rarr; its embedded chart element
	 * @param folderId      the Drive output folder id, or {@code null} for the root
	 * @param errors        collector for non-fatal per-chart errors
	 * @throws IOException when a Google API call fails irrecoverably
	 */
	void renderBreakdownChart(
			ChartClients clients, BreakdownChartRequest req, BreakdownChartJob job,
			Map<String, Map<String, ChartElementRef>> chartsBySlide, String folderId, List<String> errors)
			throws IOException {
		String seriesCode = job.seriesCode();
		BreakdownChartSeries series = seriesCode == null || seriesCode.isBlank()
				? null : BreakdownChartSeries.BY_CODE.get(seriesCode.trim().toLowerCase());
		if (series == null) {
			return;
		}
		BreakdownType type = series.section();
		String tag = "Breakdown " + series.code() + " tactic " + job.tacticNum();
		String sourceSheetId = catalog.getSourceSheetIds().get(series.code());
		Integer chartIdInSheet = parseChartId(catalog.getChartIdInSheet().get(series.code()));
		if (sourceSheetId == null || sourceSheetId.isBlank() || chartIdInSheet == null) {
			errors.add(tag + ": no chart-source spreadsheet id / chart id configured");
			return;
		}
		String slideId = naming.slideId(type, job.tacticNum());
		// The chart is picked by the workbook it links to, not by its position among the slide's elements: a
		// slide can carry several charts (the audience slide has two), and only the one linked to this series'
		// workbook may be replaced — otherwise a section's two charts would fight over the same element.
		// Absent slide and chartless slide are different failures: the first means the tactic's breakdown copy
		// was never inserted (its main tactic slide is missing), the second means the master's chart does not
		// link to the workbook this series is configured with. Reported apart so the fix is not guesswork.
		if (!chartsBySlide.containsKey(slideId)) {
			errors.add(tag + ": breakdown slide " + slideId + " is not in the deck, so its chart was not linked "
					+ "(the tactic's main slide was most likely not built)");
			return;
		}
		ChartElementRef chartRef = chartsBySlide.get(slideId).get(sourceSheetId);
		if (chartRef == null) {
			errors.add(tag + ": no chart linked to " + sourceSheetId + " found on breakdown slide " + slideId
					+ " — check that the master breakdown slide's chart really points at that workbook");
			return;
		}
		Map<String, Double> valuesByLabel = valuesByLabel(job.slices(), series.labelsFromData());
		if (valuesByLabel.isEmpty()) {
			// No data for this tactic (a DOOH tactic has no device split): drop the master's sample chart
			// rather than ship its sample bars as this tactic's numbers.
			slideChartSwapper.deleteChartFromSlide(clients.slides(), req.presentationId(), chartRef.objectId());
			return;
		}

		String copyId = driveCopier.copyFile(
				clients.drive(), sourceSheetId, tag + " — " + req.campaignTitle(), folderId);
		chartFileSharer.shareLooseCopy(clients.drive(), folderId, copyId);
		String tab = chartSpecBuilder.findDataTab(clients.sheets(), copyId);
		int written = series.labelsFromData()
				? chartSheetWriter.writeBreakdownSeries(
						clients.sheets(), copyId, tab, valuesByLabel, catalog.dataStartRowFor(series.code()))
				: chartSheetWriter.writeBreakdownImpressions(clients.sheets(), copyId, tab, rounded(valuesByLabel));
		if (written == 0) {
			errors.add(tag + ": no category labels matched the chart source — chart left empty");
			return;
		}
		Integer copyChartId = copyChartId(clients.sheets(), copyId, tab, chartIdInSheet);
		if (copyChartId == null) {
			errors.add(tag + ": the copied chart source " + copyId + " carries no chart to link");
			return;
		}
		slideChartSwapper.replaceChartOnSlide(
				clients.slides(), req.presentationId(), chartRef.objectId(), copyId, chartRef.transform(),
				copyChartId);
	}

	/**
	 * Reads the embedded charts of a copied source workbook and picks the one to link.
	 *
	 * @param sheets     the authenticated Sheets client
	 * @param copyId     the copied workbook
	 * @param dataTab    the tab the tactic's values were written to
	 * @param configured the chart id configured for the series
	 * @return the chart id to link, or {@code null} when the copy has no chart
	 * @throws IOException when the Sheets read fails
	 */
	Integer copyChartId(Sheets sheets, String copyId, String dataTab,
			int configured) throws IOException {
		Spreadsheet copy = googleCalls.execute(sheets.spreadsheets().get(copyId)
				.setIncludeGridData(false)
				.setFields("sheets(properties(title),charts(chartId))"));
		return pickChartId(copy.getSheets(), dataTab, configured);
	}

	/**
	 * Picks the chart of a source workbook to link. The configured id wins when the workbook still has it; a
	 * chart recreated in the workbook (moved to its own sheet, rebuilt, re-pasted) gets a new id that the
	 * configuration and the master slide's cached link never learn, so otherwise the first chart drawn on the
	 * data tab is taken, then the first chart anywhere in the workbook.
	 *
	 * @param tabs       the workbook's tabs with their titles and embedded charts
	 * @param dataTab    the tab holding the series' values
	 * @param configured the chart id configured for the series
	 * @return the chart id to link, or {@code null} when no tab carries a chart
	 */
	Integer pickChartId(List<Sheet> tabs, String dataTab, int configured) {
		Integer onDataTab = null;
		Integer anywhere = null;
		for (Sheet tab : tabs == null ? List.<Sheet>of() : tabs) {
			for (EmbeddedChart chart : tab.getCharts() == null ? List.<EmbeddedChart>of() : tab.getCharts()) {
				Integer id = chart.getChartId();
				if (id == null) {
					continue;
				}
				if (id == configured) {
					return id;
				}
				boolean dataTabChart = tab.getProperties() != null && dataTab != null
						&& dataTab.equals(tab.getProperties().getTitle());
				if (dataTabChart && onDataTab == null) {
					onDataTab = id;
				}
				if (anywhere == null) {
					anywhere = id;
				}
			}
		}
		return onDataTab != null ? onDataTab : anywhere;
	}

	/**
	 * Rounds a series' values to whole numbers for the label-matching writer, which pushes impressions.
	 *
	 * @param valuesByLabel normalised label &rarr; value
	 * @return the same map with each value rounded
	 */
	Map<String, Long> rounded(Map<String, Double> valuesByLabel) {
		Map<String, Long> out = new LinkedHashMap<>();
		valuesByLabel.forEach((label, value) -> out.put(label, Math.round(value)));
		return out;
	}

	/**
	 * Parses a configured in-sheet chart id, treating a null, blank or non-numeric value as unconfigured so
	 * an empty default (the feature-off state) is a safe skip rather than a startup failure.
	 *
	 * @param raw the configured chart-id text
	 * @return the chart id, or {@code null} when unset or not an integer
	 */
	Integer parseChartId(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(raw.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Indexes a job's slices by category label in sheet order, dropping non-positive values so a blank or
	 * unparseable cell never zeroes the chart. A duplicate label keeps the last slice.
	 *
	 * <p>The label is normalised only for a series that matches the workbook's own category column; a series
	 * whose labels are written into the workbook keeps them verbatim, because they land on the chart as the
	 * reader sees them.
	 *
	 * @param slices         the job's chart slices
	 * @param labelsFromData whether the labels are written into the workbook rather than matched against it
	 * @return label &rarr; value (positive only), in slice order
	 */
	Map<String, Double> valuesByLabel(List<BreakdownChartSlice> slices, boolean labelsFromData) {
		Map<String, Double> byLabel = new LinkedHashMap<>();
		if (slices == null) {
			return byLabel;
		}
		for (BreakdownChartSlice slice : slices) {
			if (slice == null || slice.value() <= 0 || slice.label() == null || slice.label().isBlank()) {
				continue;
			}
			String label = labelsFromData
					? slice.label().trim() : chartSheetWriter.normalizeBreakdownLabel(slice.label());
			byLabel.put(label, slice.value());
		}
		return byLabel;
	}

	/**
	 * Fetches the deck and maps every breakdown slide (object id prefixed {@code bd_}) to the chart elements
	 * on it — object id plus captured size/transform — keyed by the source workbook each one links to, so a
	 * caller can replace a specific chart in place.
	 *
	 * <p>Keying by workbook is what lets a slide carry more than one chart: the audience slide has an age
	 * chart and a segment chart, and each series may only touch the one linked to its own workbook. A copy
	 * inherits the master's link, which is why the link identifies the chart even though the copy's element
	 * ids are minted at duplication time. A breakdown slide with no chart element is simply absent.
	 *
	 * @param slides         the authenticated Slides client
	 * @param presentationId the deck to scan
	 * @param errors         accumulator for a read failure message
	 * @return breakdown slide object id &rarr; (source spreadsheet id &rarr; chart element); empty when the
	 *         read failed
	 */
	Map<String, Map<String, ChartElementRef>> loadBreakdownChartElements(
			Slides slides, String presentationId, List<String> errors) {
		Map<String, Map<String, ChartElementRef>> out = new LinkedHashMap<>();
		try {
			Presentation pres = googleCalls.execute(slides.presentations().get(presentationId)
					.setFields("slides.objectId,"
							+ "slides.pageElements(objectId,size,transform,sheetsChart)"));
			if (pres.getSlides() == null) {
				return out;
			}
			return chartsBySlideOf(pres.getSlides());
		} catch (IOException ex) {
			errors.add("Breakdown charts: could not read presentation layout — " + ex.getMessage());
		}
		return out;
	}

	/**
	 * Indexes the linked charts on a deck's breakdown slides, keyed by slide and then by the source workbook
	 * each chart points at. Slides that are not breakdown copies (no {@code bd_} prefix) are left out. A
	 * breakdown slide carrying no chart is kept, mapped to an empty index: the caller tells "this slide has no
	 * such chart" from "this slide was never inserted" by whether the id is a key here, and reports the two
	 * differently. Two charts on one slide linked to the same workbook are ambiguous rather than fatal — the
	 * first wins.
	 *
	 * @param slides the deck's slides in order, carrying their chart elements' ids, geometry and links
	 * @return breakdown slide object id &rarr; (source spreadsheet id &rarr; chart element)
	 */
	Map<String, Map<String, ChartElementRef>> chartsBySlideOf(List<Page> slides) {
		Map<String, Map<String, ChartElementRef>> out = new LinkedHashMap<>();
		for (Page slide : slides) {
			String slideId = slide.getObjectId();
			if (slideId == null || !slideId.startsWith("bd_")) {
				continue;
			}
			Map<String, ChartElementRef> bySource = new LinkedHashMap<>();
			for (PageElement el : slide.getPageElements() == null ? List.<PageElement>of() : slide.getPageElements()) {
				SheetsChart chart = el.getSheetsChart();
				if (chart == null || chart.getSpreadsheetId() == null || el.getObjectId() == null) {
					continue;
				}
				bySource.putIfAbsent(chart.getSpreadsheetId(), new ChartElementRef(
						el.getObjectId(), new ElementTransform(el.getSize(), el.getTransform(), slideId)));
			}
			out.put(slideId, bySource);
		}
		return out;
	}
}
