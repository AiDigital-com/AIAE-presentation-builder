package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartShape;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChart;
import com.google.api.services.sheets.v4.model.AddChartRequest;
import com.google.api.services.sheets.v4.model.BasicChartAxis;
import com.google.api.services.sheets.v4.model.BasicChartDomain;
import com.google.api.services.sheets.v4.model.BasicChartSeries;
import com.google.api.services.sheets.v4.model.BasicChartSpec;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.ChartAxisViewWindowOptions;
import com.google.api.services.sheets.v4.model.ChartData;
import com.google.api.services.sheets.v4.model.ChartSourceRange;
import com.google.api.services.sheets.v4.model.ChartSpec;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ColorStyle;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.EmbeddedObjectPosition;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.NumberFormat;
import com.google.api.services.sheets.v4.model.OverlayPosition;
import com.google.api.services.sheets.v4.model.PieChartSpec;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.SpreadsheetTheme;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.ThemeColorPair;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateSpreadsheetPropertiesRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds the one {@code batchUpdate} that turns a blank spreadsheet into a Paid Search / Paid Social chart: the theme
 * (font and accent colours, which a donut's slices take), the chart's data on the first tab, and the chart itself,
 * styled like the MPO template's sample. The chart is the last request, so its reply carries the new chart id.
 */
@Component
public class PaidChartSheetRequests {

	/** The tab id Google gives the only tab of a newly created spreadsheet. */
	static final int FIRST_SHEET_ID = 0;
	/** Font of the MPO deck's chart labels. */
	private static final String FONT = "Inter";
	/** Axis and legend label colour of the sample charts. */
	private static final String LABEL_COLOR = "#888B8F";
	/** Text and background of the chart spreadsheet's theme. */
	private static final String TEXT_COLOR = "#080808";
	private static final String BACKGROUND_COLOR = "#FFFFFF";
	/** Theme accent for the slices past a palette (the "Other" slice). */
	private static final String FILLER_COLOR = "#B4B4B4";
	/** Accent colour slots of a spreadsheet theme. */
	private static final List<String> ACCENTS =
			List.of("ACCENT1", "ACCENT2", "ACCENT3", "ACCENT4", "ACCENT5", "ACCENT6");
	/** Size of the axis and legend labels, in points. */
	private static final int LABEL_SIZE = 7;
	/** Share of a donut's radius left empty. */
	private static final double PIE_HOLE = 0.55;
	/** Column where the chart is anchored on the data tab, right of the data. */
	private static final int CHART_ANCHOR_COLUMN = 4;
	/** Number formats of the data cells. */
	private static final String COUNT_FORMAT = "#,##0";
	private static final String PERCENT_FORMAT = "0.0%";
	private static final String MONEY_FORMAT = "$#,##0";
	private static final String NUMBER_TYPE = "NUMBER";
	private static final String PERCENT_TYPE = "PERCENT";
	private static final String CURRENCY_TYPE = "CURRENCY";
	/** Column headers, which the column charts print in their legend. */
	private static final String MONTH_HEADER = "Month";
	private static final String CLICKS_HEADER = "Clicks (bars)";
	private static final String CTR_HEADER = "CTR (line)";
	private static final String SPEND_HEADER = "Spend (bars)";
	private static final String SLICE_HEADER = "Slice";
	private static final String CONVERSIONS_HEADER = "Conversions";
	private static final double CHANNEL_MAX = 255d;

	/**
	 * @param chart       the chart's data
	 * @param style       how the chart is drawn
	 * @param widthPixels the chart's width in the spreadsheet, matching the slide element it replaces
	 * @param heightPixels the chart's height in the spreadsheet
	 * @return theme, data and chart requests, in that order
	 */
	public List<Request> requests(PaidChart chart, PaidChartStyle style, int widthPixels, int heightPixels) {
		List<Request> requests = new ArrayList<>();
		requests.add(new Request().setUpdateSpreadsheetProperties(new UpdateSpreadsheetPropertiesRequest()
				.setProperties(new SpreadsheetProperties().setSpreadsheetTheme(theme(style)))
				.setFields("spreadsheetTheme")));
		requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
				.setStart(new GridCoordinate().setSheetId(FIRST_SHEET_ID).setRowIndex(0).setColumnIndex(0))
				.setRows(rows(chart))
				.setFields("userEnteredValue,userEnteredFormat.numberFormat")));
		requests.add(new Request().setAddChart(new AddChartRequest().setChart(new EmbeddedChart()
				.setSpec(spec(chart, style))
				.setPosition(new EmbeddedObjectPosition().setOverlayPosition(new OverlayPosition()
						.setAnchorCell(new GridCoordinate().setSheetId(FIRST_SHEET_ID).setRowIndex(0)
								.setColumnIndex(CHART_ANCHOR_COLUMN))
						.setWidthPixels(widthPixels)
						.setHeightPixels(heightPixels))))));
		return requests;
	}

	/**
	 * @param style the chart's style
	 * @return a theme whose accents are the style's colours in order, padded with a neutral grey
	 */
	SpreadsheetTheme theme(PaidChartStyle style) {
		List<ThemeColorPair> pairs = new ArrayList<>();
		pairs.add(pair("TEXT", TEXT_COLOR));
		pairs.add(pair("BACKGROUND", BACKGROUND_COLOR));
		for (int i = 0; i < ACCENTS.size(); i++) {
			pairs.add(pair(ACCENTS.get(i), i < style.colors().size() ? style.colors().get(i) : FILLER_COLOR));
		}
		pairs.add(pair("LINK", style.colors().get(0)));
		return new SpreadsheetTheme().setPrimaryFontFamily(FONT).setThemeColors(pairs);
	}

	/**
	 * @param chart the chart's data
	 * @return a header row, then one row per point: label, value and, for clicks &amp; CTR, the CTR
	 */
	List<RowData> rows(PaidChart chart) {
		PaidChartShape type = chart.marker().type();
		List<RowData> rows = new ArrayList<>();
		rows.add(new RowData().setValues(switch (type) {
			case CLICKS_CTR -> List.of(text(MONTH_HEADER), text(CLICKS_HEADER), text(CTR_HEADER));
			case SPEND -> List.of(text(MONTH_HEADER), text(SPEND_HEADER));
			case DONUT -> List.of(text(SLICE_HEADER), text(CONVERSIONS_HEADER));
		}));
		for (int i = 0; i < chart.labels().size(); i++) {
			CellData label = text(chart.labels().get(i));
			double value = chart.values().get(i);
			rows.add(new RowData().setValues(switch (type) {
				case CLICKS_CTR -> List.of(label, number(value, NUMBER_TYPE, COUNT_FORMAT),
						number(chart.lineValues().get(i), PERCENT_TYPE, PERCENT_FORMAT));
				case SPEND -> List.of(label, number(value, CURRENCY_TYPE, MONEY_FORMAT));
				case DONUT -> List.of(label, number(value, NUMBER_TYPE, COUNT_FORMAT));
			}));
		}
		return rows;
	}

	/**
	 * @param chart the chart's data
	 * @param style the chart's style
	 * @return the chart spec: a column/line combo, a column chart or a donut
	 */
	ChartSpec spec(PaidChart chart, PaidChartStyle style) {
		int points = chart.labels().size();
		ChartSpec spec = new ChartSpec().setFontName(FONT).setMaximized(true)
				.setBackgroundColorStyle(colorStyle(BACKGROUND_COLOR));
		if (chart.marker().type() == PaidChartShape.DONUT) {
			return spec.setPieChart(new PieChartSpec()
					.setLegendPosition("NO_LEGEND")
					.setPieHole(PIE_HOLE)
					.setDomain(source(0, 1, points + 1))
					.setSeries(source(1, 1, points + 1)));
		}
		List<BasicChartSeries> series = new ArrayList<>();
		series.add(new BasicChartSeries().setType("COLUMN").setTargetAxis("LEFT_AXIS")
				.setColorStyle(colorStyle(style.colors().get(0))).setSeries(source(1, 0, points + 1)));
		boolean combo = chart.marker().type() == PaidChartShape.CLICKS_CTR;
		if (combo) {
			series.add(new BasicChartSeries().setType("LINE").setTargetAxis("RIGHT_AXIS")
					.setColorStyle(colorStyle(style.colors().get(1))).setSeries(source(2, 0, points + 1)));
		}
		List<BasicChartAxis> axes = new ArrayList<>();
		axes.add(axis("BOTTOM_AXIS"));
		axes.add(axis("LEFT_AXIS").setViewWindowOptions(
				new ChartAxisViewWindowOptions().setViewWindowMode("EXPLICIT").setViewWindowMin(0d)));
		if (combo) {
			axes.add(axis("RIGHT_AXIS"));
		}
		return spec.setBasicChart(new BasicChartSpec()
				.setChartType(combo ? "COMBO" : "COLUMN")
				.setLegendPosition("TOP_LEGEND")
				.setHeaderCount(1)
				.setAxis(axes)
				.setDomains(List.of(new BasicChartDomain().setDomain(source(0, 0, points + 1))))
				.setSeries(series));
	}

	/**
	 * @param position the axis position, e.g. {@code "LEFT_AXIS"}
	 * @return an untitled axis with the sample's small grey labels
	 */
	BasicChartAxis axis(String position) {
		return new BasicChartAxis().setPosition(position).setFormat(new TextFormat()
				.setFontFamily(FONT).setFontSize(LABEL_SIZE).setForegroundColorStyle(colorStyle(LABEL_COLOR)));
	}

	/**
	 * @param column   zero-based column of the first tab
	 * @param startRow inclusive zero-based start row
	 * @param endRow   exclusive zero-based end row
	 * @return chart data reading that column range
	 */
	ChartData source(int column, int startRow, int endRow) {
		return new ChartData().setSourceRange(new ChartSourceRange().setSources(List.of(new GridRange()
				.setSheetId(FIRST_SHEET_ID).setStartRowIndex(startRow).setEndRowIndex(endRow)
				.setStartColumnIndex(column).setEndColumnIndex(column + 1))));
	}

	/**
	 * @param themeColorType the theme slot, e.g. {@code "ACCENT1"}
	 * @param hex            the colour, {@code #RRGGBB}
	 * @return the theme colour pair
	 */
	ThemeColorPair pair(String themeColorType, String hex) {
		return new ThemeColorPair().setColorType(themeColorType).setColor(colorStyle(hex));
	}

	/**
	 * @param hex a colour, {@code #RRGGBB}
	 * @return the colour as a Sheets colour style
	 */
	ColorStyle colorStyle(String hex) {
		return new ColorStyle().setRgbColor(color(hex));
	}

	/**
	 * @param hex a colour, {@code #RRGGBB}
	 * @return the colour with 0..1 channels
	 */
	Color color(String hex) {
		byte[] rgb = HexFormat.of().parseHex(hex.substring(1));
		return new Color()
				.setRed(channel(rgb[0]))
				.setGreen(channel(rgb[1]))
				.setBlue(channel(rgb[2]));
	}

	/**
	 * @param value one colour channel byte
	 * @return the channel as a 0..1 fraction
	 */
	float channel(byte value) {
		return (float) (Byte.toUnsignedInt(value) / CHANNEL_MAX);
	}

	/**
	 * @param value a cell's text
	 * @return a text cell
	 */
	CellData text(String value) {
		return new CellData().setUserEnteredValue(new ExtendedValue().setStringValue(value));
	}

	/**
	 * @param value   the number
	 * @param type    the number format type, e.g. {@code "PERCENT"}
	 * @param pattern the number format pattern, e.g. {@code "0.0%"}
	 * @return a formatted number cell, whose format the chart's labels follow
	 */
	CellData number(double value, String type, String pattern) {
		return new CellData().setUserEnteredValue(new ExtendedValue().setNumberValue(value))
				.setUserEnteredFormat(new CellFormat().setNumberFormat(new NumberFormat()
						.setType(type).setPattern(pattern)));
	}
}
