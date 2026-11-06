package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import com.aidigital.reportconstructor.service.reports.ports.PaidChartRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Response;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Links the MPO deck's Paid Search / Paid Social charts in place of the template's sample chart images. Each chart
 * gets its own small spreadsheet (a donut's slice colours come from its spreadsheet's theme, and the donuts use
 * different palettes), filled and charted in one {@code batchUpdate}, all in one Drive folder per deck.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaidChartBuilder {

	/** Fields read to find the sample images: ids, alt-text titles, sizes and positions, in z-order. */
	private static final String SLOT_FIELDS = "slides(objectId,pageElements(objectId,title,size,transform))";
	/** Prefix of a linked chart's object id. */
	private static final String CHART_ID_PREFIX = "paidch_";
	/** Length of the random part of a linked chart's object id. */
	private static final int CHART_ID_RANDOM_LENGTH = 20;

	private final DriveCopier driveCopier;
	private final ChartFileSharer chartFileSharer;
	private final ChartErrorTranslator chartErrors;
	private final PaidChartSheetRequests sheetRequests;
	private final PaidChartSlideRequests slideRequests;

	/**
	 * @param clients authenticated Drive, Sheets and Slides clients
	 * @param req     the deck and its paid charts
	 * @return one message per chart that could not be linked; empty when all were
	 */
	public List<String> buildPaidCharts(ChartClients clients, PaidChartRequest req) {
		List<String> errors = new ArrayList<>();
		if (req.charts() == null || req.charts().isEmpty()) {
			return errors;
		}
		Set<String> markers = req.charts().stream().map(c -> c.marker().marker()).collect(Collectors.toSet());
		Map<String, PaidChartSlot> slots;
		try {
			Presentation deck = clients.slides().presentations().get(req.presentationId())
					.setFields(SLOT_FIELDS).execute();
			slots = slideRequests.slots(deck.getSlides(), markers);
		} catch (IOException ex) {
			errors.add("Paid charts: could not read presentation layout — " + ex.getMessage());
			return errors;
		}
		String folderId = null;
		for (PaidSlideChart chart : req.charts()) {
			PaidChartSlot slot = slots.get(chart.marker().marker());
			if (slot == null) {
				// The deck has no such chart: the EOM deck carries no overview slides.
				continue;
			}
			String tag = "Paid chart " + chart.marker().marker();
			try {
				if (!chart.hasData()) {
					// No data for the chart (a missing tab, or no delivery in the window): drop the sample image
					// rather than ship its sample numbers.
					deleteImage(clients, req.presentationId(), slot);
					continue;
				}
				if (folderId == null) {
					folderId = folder(clients, req.campaignTitle());
				}
				linkChart(clients, req, chart, slot, folderId);
			} catch (IOException | RuntimeException ex) {
				errors.add(chartErrors.describeChartError(tag, ex));
			}
		}
		return errors;
	}

	/**
	 * Creates the chart's spreadsheet, draws the chart in it and swaps the sample image for it.
	 *
	 * @param clients  authenticated clients
	 * @param req      the deck being finished
	 * @param chart    the chart's data
	 * @param slot     the sample image the chart replaces
	 * @param folderId the folder holding the deck's chart spreadsheets, or empty when it could not be created
	 * @throws IOException if a Google call fails
	 */
	void linkChart(ChartClients clients, PaidChartRequest req, PaidSlideChart chart, PaidChartSlot slot,
			String folderId) throws IOException {
		PaidChartStyle style = PaidChartStyle.of(chart.marker());
		String spreadsheetId = driveCopier.createSpreadsheet(clients.drive(),
				chart.marker().marker() + " — " + req.campaignTitle(), folderId);
		chartFileSharer.shareLooseCopy(clients.drive(), folderId, spreadsheetId);
		double[] box = slideRequests.box(slot, style);
		BatchUpdateSpreadsheetResponse reply = clients.sheets().spreadsheets().batchUpdate(spreadsheetId,
				new BatchUpdateSpreadsheetRequest().setRequests(sheetRequests.requests(chart, style,
						slideRequests.pixels(box[2]), slideRequests.pixels(box[3])))).execute();
		Integer chartId = chartId(reply);
		if (chartId == null) {
			throw new IllegalStateException("the chart spreadsheet " + spreadsheetId + " returned no chart id");
		}
		String newObjectId = CHART_ID_PREFIX
				+ UUID.randomUUID().toString().replace("-", "").substring(0, CHART_ID_RANDOM_LENGTH);
		List<Request> swap = slideRequests.swap(slot, style, spreadsheetId, chartId, newObjectId, chart.centerValue());
		clients.slides().presentations().batchUpdate(req.presentationId(),
				new BatchUpdatePresentationRequest().setRequests(swap)).execute();
	}

	/**
	 * @param reply the chart spreadsheet's {@code batchUpdate} reply
	 * @return the id of the chart the last request added, or {@code null} when the reply carries none
	 */
	Integer chartId(BatchUpdateSpreadsheetResponse reply) {
		List<Response> replies = reply == null || reply.getReplies() == null ? List.of() : reply.getReplies();
		for (int i = replies.size() - 1; i >= 0; i--) {
			Response r = replies.get(i);
			if (r != null && r.getAddChart() != null && r.getAddChart().getChart() != null) {
				return r.getAddChart().getChart().getChartId();
			}
		}
		return null;
	}

	/**
	 * @param clients       authenticated clients
	 * @param campaignTitle the deck title
	 * @return the shared folder for the deck's chart spreadsheets, or empty when it could not be created (the
	 *         spreadsheets then go to the caller's root and are shared one by one)
	 */
	String folder(ChartClients clients, String campaignTitle) {
		try {
			String folderId = driveCopier.createFolder(clients.drive(), "Paid charts — " + campaignTitle);
			chartFileSharer.shareFolder(clients.drive(), folderId);
			return folderId;
		} catch (IOException ex) {
			log.warn("[paid-charts] could not create folder, spreadsheets go to root: {}", ex.getMessage());
			return "";
		}
	}

	/**
	 * @param clients        authenticated clients
	 * @param presentationId the deck
	 * @param slot           the sample image to delete
	 * @throws IOException if the Slides call fails
	 */
	void deleteImage(ChartClients clients, String presentationId, PaidChartSlot slot) throws IOException {
		clients.slides().presentations().batchUpdate(presentationId, new BatchUpdatePresentationRequest()
				.setRequests(List.of(new Request().setDeleteObject(
						new DeleteObjectRequest().setObjectId(slot.objectId()))))).execute();
	}
}
