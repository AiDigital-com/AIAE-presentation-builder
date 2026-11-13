package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BasicChartDomain;
import com.google.api.services.sheets.v4.model.BasicChartSeries;
import com.google.api.services.sheets.v4.model.BasicChartSpec;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ChartData;
import com.google.api.services.sheets.v4.model.ChartSourceRange;
import com.google.api.services.sheets.v4.model.ChartSpec;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateChartSpecRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChartSpecBuilderTest {

	private static final String CHART_FIELDS = "sheets(properties(sheetId,title),charts(chartId,spec))";
	private static final String TITLE_FIELDS = "sheets.properties.title";

	@Test
	void shouldReturnSpecOfChartMatchingConfiguredIdTest() throws IOException {
		// Given: a tab without charts, then a tab whose third chart carries the configured id
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setChartIdInSheet(77);
		ChartSpecBuilder builder = new ChartSpecBuilder(catalog,
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		ChartSpec wanted = new ChartSpec().setTitle("Daily pacing");
		Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
				new Sheet(),
				new Sheet().setCharts(List.of(
						new EmbeddedChart().setSpec(new ChartSpec().setTitle("no id")),
						new EmbeddedChart().setChartId(12).setSpec(new ChartSpec().setTitle("other")),
						new EmbeddedChart().setChartId(77).setSpec(wanted)))));
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false).setFields(CHART_FIELDS).execute())
				.thenReturn(spreadsheet);

		// When:
		ChartSpec spec = builder.readChartSpec(sheets, "ss-1");

		// Then:
		assertThat(spec).isSameAs(wanted);
	}

	@Test
	void shouldReturnNullWhenNoChartMatchesConfiguredIdTest() throws IOException {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setChartIdInSheet(77);
		ChartSpecBuilder builder = new ChartSpecBuilder(catalog,
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
				new Sheet().setCharts(List.of(new EmbeddedChart().setChartId(12).setSpec(new ChartSpec())))));
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false).setFields(CHART_FIELDS).execute())
				.thenReturn(spreadsheet);

		// When:
		ChartSpec spec = builder.readChartSpec(sheets, "ss-1");

		// Then:
		assertThat(spec).isNull();
	}

	@Test
	void shouldReturnNullChartSpecWhenSpreadsheetHasNoSheetsTest() throws IOException {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setChartIdInSheet(77);
		ChartSpecBuilder builder = new ChartSpecBuilder(catalog,
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false).setFields(CHART_FIELDS).execute())
				.thenReturn(new Spreadsheet());

		// When:
		ChartSpec spec = builder.readChartSpec(sheets, "ss-1");

		// Then:
		assertThat(spec).isNull();
	}

	@Test
	void shouldPreferSheet1DataTabOverEarlierTabsTest() throws IOException {
		// Given: untitled entries are skipped, and Sheet1 wins over the first titled tab
		ChartSpecBuilder builder = new ChartSpecBuilder(new ChartTemplateCatalog(),
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
				new Sheet(),
				new Sheet().setProperties(new SheetProperties()),
				new Sheet().setProperties(new SheetProperties().setTitle("Data")),
				new Sheet().setProperties(new SheetProperties().setTitle("Sheet1"))));
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false).setFields(TITLE_FIELDS).execute())
				.thenReturn(spreadsheet);

		// When:
		String tab = builder.findDataTab(sheets, "ss-1");

		// Then:
		assertThat(tab).isEqualTo("Sheet1");
	}

	@Test
	void shouldFallBackToFirstTitledTabWhenSheet1IsAbsentTest() throws IOException {
		// Given:
		ChartSpecBuilder builder = new ChartSpecBuilder(new ChartTemplateCatalog(),
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
				new Sheet().setProperties(new SheetProperties()),
				new Sheet().setProperties(new SheetProperties().setTitle("Data")),
				new Sheet().setProperties(new SheetProperties().setTitle("Notes"))));
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false).setFields(TITLE_FIELDS).execute())
				.thenReturn(spreadsheet);

		// When:
		String tab = builder.findDataTab(sheets, "ss-1");

		// Then:
		assertThat(tab).isEqualTo("Data");
	}

	@Test
	void shouldDefaultDataTabToSheet1WhenSpreadsheetHasNoSheetsTest() throws IOException {
		// Given:
		ChartSpecBuilder builder = new ChartSpecBuilder(new ChartTemplateCatalog(),
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false).setFields(TITLE_FIELDS).execute())
				.thenReturn(new Spreadsheet());

		// When:
		String tab = builder.findDataTab(sheets, "ss-1");

		// Then:
		assertThat(tab).isEqualTo("Sheet1");
	}

	@Test
	void shouldInjectImpressionColumnsAndRateLineOverTemplateDomainRowsTest() {
		// Given: a combo template whose domain covers rows 1..32 of another sheet, with a configured line color
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		RgbColor lineColor = new RgbColor();
		lineColor.setRed(0.5);
		lineColor.setGreen(0.25);
		lineColor.setBlue(0.75);
		catalog.setComboLineColor(lineColor);
		ChartSpecBuilder builder = new ChartSpecBuilder(catalog,
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		GridRange domainRange = new GridRange().setSheetId(999).setStartRowIndex(1).setEndRowIndex(32)
				.setStartColumnIndex(0).setEndColumnIndex(1);
		BasicChartSpec basic = new BasicChartSpec().setDomains(List.of(new BasicChartDomain().setDomain(
				new ChartData().setSourceRange(new ChartSourceRange().setSources(List.of(domainRange))))));
		ChartSpec spec = new ChartSpec().setBasicChart(basic);

		// When:
		builder.injectComboSeries(spec, 42, true);

		// Then: the domain is retargeted and both series read the copy's data tab over the domain rows
		assertThat(domainRange.getSheetId()).isEqualTo(42);
		assertThat(basic.getHeaderCount()).isEqualTo(1);
		List<BasicChartSeries> series = basic.getSeries();
		assertThat(series).hasSize(2);
		assertThat(series).extracting(BasicChartSeries::getType).containsExactly("COLUMN", "LINE");
		assertThat(series).extracting(BasicChartSeries::getTargetAxis).containsExactly("LEFT_AXIS", "RIGHT_AXIS");
		assertThat(series.get(0).getSeries().getSourceRange().getSources()).containsExactly(new GridRange()
				.setSheetId(42).setStartRowIndex(1).setEndRowIndex(32).setStartColumnIndex(1).setEndColumnIndex(2));
		assertThat(series.get(1).getSeries().getSourceRange().getSources()).containsExactly(new GridRange()
				.setSheetId(42).setStartRowIndex(1).setEndRowIndex(32).setStartColumnIndex(2).setEndColumnIndex(3));
		assertThat(series.get(0).getColor().getRed()).isEqualTo(0.0f);
		assertThat(series.get(0).getColor().getGreen()).isEqualTo(0.035294f);
		assertThat(series.get(0).getColor().getBlue()).isEqualTo(0.858824f);
		assertThat(series.get(0).getColorStyle().getRgbColor()).isEqualTo(series.get(0).getColor());
		assertThat(series.get(1).getColor().getRed()).isEqualTo(0.5f);
		assertThat(series.get(1).getColor().getGreen()).isEqualTo(0.25f);
		assertThat(series.get(1).getColor().getBlue()).isEqualTo(0.75f);
		assertThat(series.get(1).getColorStyle().getRgbColor()).isEqualTo(series.get(1).getColor());
	}

	@Test
	void shouldInjectOnlyImpressionColumnsOverDefaultRowsWhenDomainHasNoSourceRangeTest() {
		// Given: a domain without a source range, and a template that already declares two header rows
		ChartSpecBuilder builder = new ChartSpecBuilder(new ChartTemplateCatalog(),
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		BasicChartSpec basic = new BasicChartSpec().setHeaderCount(2)
				.setDomains(List.of(new BasicChartDomain().setDomain(new ChartData())));
		ChartSpec spec = new ChartSpec().setBasicChart(basic);

		// When:
		builder.injectComboSeries(spec, 7, false);

		// Then:
		assertThat(basic.getHeaderCount()).isEqualTo(2);
		assertThat(basic.getSeries()).hasSize(1);
		assertThat(basic.getSeries().getFirst().getType()).isEqualTo("COLUMN");
		assertThat(basic.getSeries().getFirst().getSeries().getSourceRange().getSources()).containsExactly(
				new GridRange().setSheetId(7).setStartRowIndex(0).setEndRowIndex(50)
						.setStartColumnIndex(1).setEndColumnIndex(2));
	}

	@Test
	void shouldUseDefaultRowsWhenDomainLacksDataOrRangeBoundsTest() {
		// Given: a domain without chart data, one with empty sources, and one whose range has no row bounds
		ChartSpecBuilder builder = new ChartSpecBuilder(new ChartTemplateCatalog(),
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		BasicChartSpec noData = new BasicChartSpec().setDomains(List.of(new BasicChartDomain()));
		BasicChartSpec noSources = new BasicChartSpec().setDomains(List.of(new BasicChartDomain()
				.setDomain(new ChartData().setSourceRange(new ChartSourceRange().setSources(List.of())))));
		BasicChartSpec nullSources = new BasicChartSpec().setDomains(List.of(new BasicChartDomain()
				.setDomain(new ChartData().setSourceRange(new ChartSourceRange()))));
		GridRange unbounded = new GridRange().setSheetId(999);
		BasicChartSpec noBounds = new BasicChartSpec().setDomains(List.of(new BasicChartDomain()
				.setDomain(new ChartData().setSourceRange(new ChartSourceRange().setSources(List.of(unbounded))))));

		// When:
		builder.injectComboSeries(new ChartSpec().setBasicChart(noData), 3, false);
		builder.injectComboSeries(new ChartSpec().setBasicChart(noSources), 3, false);
		builder.injectComboSeries(new ChartSpec().setBasicChart(nullSources), 3, false);
		builder.injectComboSeries(new ChartSpec().setBasicChart(noBounds), 3, false);

		// Then: every series spans the default rows 0..50, and the unbounded domain is still retargeted
		GridRange expected = new GridRange().setSheetId(3).setStartRowIndex(0).setEndRowIndex(50)
				.setStartColumnIndex(1).setEndColumnIndex(2);
		assertThat(List.of(noData, noSources, nullSources, noBounds))
				.extracting(bc -> bc.getSeries().getFirst().getSeries().getSourceRange().getSources().getFirst())
				.containsOnly(expected);
		assertThat(unbounded.getSheetId()).isEqualTo(3);
	}

	@Test
	void shouldLeaveSpecsWithoutBasicChartDomainsUntouchedTest() {
		// Given:
		ChartSpecBuilder builder = new ChartSpecBuilder(new ChartTemplateCatalog(),
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		ChartSpec pie = new ChartSpec().setTitle("pie");
		BasicChartSpec nullDomains = new BasicChartSpec();
		BasicChartSpec emptyDomains = new BasicChartSpec().setDomains(List.of());

		// When:
		builder.injectComboSeries(pie, 3, true);
		builder.injectComboSeries(new ChartSpec().setBasicChart(nullDomains), 3, true);
		builder.injectComboSeries(new ChartSpec().setBasicChart(emptyDomains), 3, true);

		// Then:
		assertThat(pie.getBasicChart()).isNull();
		assertThat(nullDomains.getSeries()).isNull();
		assertThat(nullDomains.getHeaderCount()).isNull();
		assertThat(emptyDomains.getSeries()).isNull();
		assertThat(emptyDomains.getHeaderCount()).isNull();
		assertThatCode(() -> builder.injectComboSeries(null, 3, true)).doesNotThrowAnyException();
	}

	@Test
	void shouldApplySpecToConfiguredChartIdTest() throws IOException {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setChartIdInSheet(77);
		ChartSpecBuilder builder = new ChartSpecBuilder(catalog,
				new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		ChartSpec spec = new ChartSpec().setTitle("Daily pacing");
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		ArgumentCaptor<BatchUpdateSpreadsheetRequest> body = ArgumentCaptor.forClass(BatchUpdateSpreadsheetRequest.class);

		// When:
		builder.applyChartSpec(sheets, "ss-1", spec);

		// Then:
		verify(sheets.spreadsheets()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getRequests()).hasSize(1);
		UpdateChartSpecRequest update = body.getValue().getRequests().getFirst().getUpdateChartSpec();
		assertThat(update.getChartId()).isEqualTo(77);
		assertThat(update.getSpec()).isSameAs(spec);
	}
}
