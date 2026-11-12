package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.drive.DriveSharer;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveShareRecipients;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckTemplate;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckFill;
import com.aidigital.reportconstructor.externalservices.google.slides.SlidesDeckClient;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DuplicateObjectRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.ReplaceAllTextRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.SubstringMatchCriteria;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.UpdateSlidesPositionRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Google Slides + Drive implementation. Clones {@code SLIDES_TEMPLATE_ID} (or
 * {@code EOM_SLIDES_TEMPLATE_ID} for an EOM report, see {@link #templateIdFor})
 * into a new deck named after the job, runs {@code replaceAllText} for every
 * {@code {token}} → value pair, and returns the public Slides edit URL.
 *
 * <p>Activated when {@link GoogleCredentialsFactory} is on the context.
 * Falls back to {@code StubSlidesProvider} otherwise.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealSlidesProvider implements SlidesDeckClient {

	private static final String APPLICATION_NAME = "Report Constructor — AI Digital";

	/** Max tactics the deck template carries (per-tactic detail slides 1..28). */
	private static final int MAX_TACTICS = 28;

	/**
	 * Max requests sent in a single {@code batchUpdate}. Mirrors the Sheets provider: a full 28-tactic
	 * report expands to ~800 {@code replaceAllText} operations, and packing them into one atomic
	 * batchUpdate risks repeated 500/503 {@code backendError}s from Slides under the payload weight (the
	 * same failure that hit the Sheets step on job 128). Splitting into fixed-size chunks — each its own
	 * batchUpdate, retried independently — keeps every request small enough for Slides to accept. Safe
	 * because replaceAllText targets disjoint tokens and the trim's ordered deletes stay in sequence
	 * across chunk boundaries, so chunking never changes the outcome.
	 */
	private static final int BATCH_UPDATE_CHUNK_SIZE = 100;

	/** Tactics per group; the deck carries one summary slide + one "Our results" slide per group. */
	private static final int TACTICS_PER_GROUP = 7;

	/** Number of tactic groups (28 tactics / 7 per group). */
	private static final int GROUP_COUNT = 4;

	/**
	 * Field mask for the {@code presentations.get} used by {@link #applyDeckMarkup}: each shape's id and its
	 * text runs with their start indices, which is what locating a marker in the shape's text needs, plus each
	 * element's geometry and fill so a lead takeaway on a dark panel can switch to the on-dark palette.
	 */
	private static final String BOLD_MARKUP_FIELDS = "slides.pageElements(objectId,size,transform,"
			+ "shape(shapeProperties.shapeBackgroundFill,text.textElements(startIndex,textRun.content)))";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #addBreakdownSlides} and
	 * {@link #addTacticSlides}: only the slide order ({@code objectId}) and the text needed to discover
	 * {@code {{…}}} tokens on the master slides (shape text and table-cell text). Keeping the mask tight
	 * avoids pulling the whole deck back.
	 */
	private static final String BREAKDOWN_FIELDS =
			"slides.objectId,"
			+ "slides.pageElements.shape.text.textElements.textRun.content,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #surplusRowRequests}: the summary table's
	 * cell text, plus — and this is the part that matters — each page element's own {@code objectId}. A field
	 * mask returns nothing it does not name, so reading the table text without the element ids gives back
	 * elements with a null id, the configured table is never recognized among them, and the row trim silently
	 * skips every deck: that is exactly how a two-tactic deck shipped with five raw {@code {{tactic N}}} rows.
	 */
	private static final String TABLE_TRIM_FIELDS =
			"slides.pageElements.objectId,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #trimEomDashboardSlides}: the slide order
	 * and text of {@link #BREAKDOWN_FIELDS}, plus each page element's own {@code objectId} — the trim both
	 * deletes whole slides (needing the slide ids) and deletes rows from tables it discovers by text
	 * (needing the element ids); a mask returns nothing it does not name.
	 */
	private static final String EOM_TRIM_FIELDS =
			"slides.objectId,"
			+ "slides.pageElements.objectId,"
			+ "slides.pageElements.shape.text.textElements.textRun.content,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #deleteMasterSlides}: only the slide
	 * order ({@code objectId}) is needed to check which configured masters are present in the deck.
	 */
	private static final String MASTER_FIELDS = "slides.objectId";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #deleteReportTypeSlides}: the slide order
	 * ({@code objectId}) plus the shape and table text the configured titles are matched against, and each
	 * element's id, size and transform — the geometry the dropped blocks are located by. Groups bring their
	 * children's geometry and images report themselves, which is what the tactic-slide relayout measures and
	 * resizes.
	 */
	private static final String TITLE_FIELDS =
			"slides.objectId,"
			+ "slides.pageElements(objectId,size,transform,image.contentUrl,shape.shapeType,"
			+ "shape.text.textElements.textRun.content,"
			+ "elementGroup.children(objectId,size,transform,shape.shapeType,"
			+ "shape.text.textElements.textRun.content),"
			+ "table.tableRows.tableCells.text.textElements.textRun.content)";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #compactDeviceTables}: each slide id plus the
	 * element ids, sizes, transforms and shape text the device table's rows are recovered from.
	 */
	private static final String DEVICE_TABLE_FIELDS =
			"slides.objectId,"
			+ "slides.pageElements(objectId,size,transform,shape.shapeType,shape.text.textElements.textRun.content,"
			+ "table.tableRows.tableCells.text.textElements.textRun.content)";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #fillPaidSlides}: slide ids, each element's id and
	 * alt-text title (the table markers), shape text inside groups two levels deep (the tokens and divider titles a
	 * channel's slides are found by), and table-cell text (the current value each cell write replaces).
	 */
	private static final String PAID_FIELDS = "slides(objectId,pageElements(objectId,title,"
			+ "shape.text.textElements.textRun.content,"
			+ "table.tableRows.tableCells.text.textElements.textRun.content,"
			+ "elementGroup.children(objectId,title,shape.text.textElements.textRun.content,"
			+ "elementGroup.children(objectId,title,shape.text.textElements.textRun.content))))";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #dashUnresolvedTokens}: the text of
	 * {@link #BREAKDOWN_FIELDS} plus the shape text inside groups, three levels deep, so a token in a grouped shape
	 * is swept too.
	 */
	private static final String DASH_FIELDS = BREAKDOWN_FIELDS + ","
			+ "slides.pageElements.elementGroup.children(shape.text.textElements.textRun.content,"
			+ "elementGroup.children(shape.text.textElements.textRun.content,"
			+ "elementGroup.children(shape.text.textElements.textRun.content)))";

	/** Startup-log marker for a master slide that is not configured. */
	private static final String UNSET_MARKER = "(unset)";

	/** Prefix the Slides editor puts before a slide's object id in its {@code #slide=id.…} URL fragment. */
	private static final String SLIDE_URL_ID_PREFIX = "id.";

	/** Everything that is not a letter or a digit, stripped before matching a slide title. */
	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

	/** Matches a whole {@code {{…}}} placeholder token (no nested braces). */
	private static final Pattern TOKEN = Pattern.compile("\\{\\{[^{}]*\\}\\}");

	/** What an unresolved token is replaced with by the final sweep: the deck's own "no figure" mark. */
	private static final String DASH = "\u2014";

	/** The percent sign some master slides type right after a stat-tile token ({@code {{dev_n_ctr}} %}). */
	private static final String PERCENT = "%";

	/**
	 * The ways a master slide follows a token with its own percent sign, longest first so the spaced form is
	 * consumed before the bare one could match inside it.
	 */
	private static final List<String> PERCENT_SUFFIXES = List.of(" " + PERCENT, PERCENT);

	/**
	 * Matches the standalone tactic variable {@code n} inside a token: a single {@code n} or {@code N}
	 * bounded on both sides by a token delimiter ({@code _}, {@code .}, whitespace, or a brace), so the
	 * {@code n} in words like {@code name} or {@code imps} is never touched. Both cases are accepted
	 * because master templates use either (e.g. {@code {{tactic n}}} vs {@code {{tactic N}}}). Replaced
	 * with the tactic number.
	 */
	private static final Pattern N_VARIABLE = Pattern.compile("(?<=[_.\\s{])[nN](?=[_.\\s}])");

	private final GoogleCredentialsFactory creds;
	private final GoogleRequestRetrier retrier;
	private final DriveSharer driveSharer;
	private final DriveShareRecipients shareRecipients;
	private final Slides slides;
	private final Drive drive;
	private final String slidesTemplateId;
	private final EomDeckPolicy eomDeckPolicy;
	private final MpoDeckPolicy mpoDeckPolicy;
	private final EomSlideFinder eomSlideFinder;
	private final String targetFolderId;
	private final SlideModel standardModel;
	private final SlideModel mpoEocModel;
	private final Set<String> eomDropSlideObjectIds;
	private final List<String> eomDropSlideTitles;
	private final List<String> eomDropElementLabels;
	private final String eomRelayoutGrowLabel;
	private final String eomRelayoutAnchorLabel;
	private final SlideBlockLocator slideBlockLocator;
	private final TacticSlideRelayout tacticSlideRelayout;
	private final DeviceTableCompactor deviceTableCompactor;
	private final CreativeTableCompactor creativeTableCompactor;
	private final BreakdownSlideNaming breakdownSlideNaming;
	private final SummaryTableRowTrimmer summaryTableRowTrimmer;
	private final DeckMarkupRequestBuilder deckMarkup;
	private final PaidSlidesWriter paidSlidesWriter;

	public RealSlidesProvider(
			GoogleCredentialsFactory creds, GoogleProperties props, DriveSharer driveSharer,
			DriveShareRecipients shareRecipients, GoogleRequestRetrier retrier,
			BreakdownSlideNaming breakdownSlideNaming,
			SummaryTableRowTrimmer summaryTableRowTrimmer, EomDeckPolicy eomDeckPolicy,
			MpoDeckPolicy mpoDeckPolicy, EomSlideFinder eomSlideFinder, DeckMarkupRequestBuilder deckMarkup,
			SlideBlockLocator slideBlockLocator, TacticSlideRelayout tacticSlideRelayout,
			DeviceTableCompactor deviceTableCompactor, CreativeTableCompactor creativeTableCompactor,
			PaidSlidesWriter paidSlidesWriter) {
		String slidesTemplateId = props.getSlidesTemplateId();
		this.paidSlidesWriter = paidSlidesWriter;
		this.eomDeckPolicy = eomDeckPolicy;
		this.mpoDeckPolicy = mpoDeckPolicy;
		this.eomSlideFinder = eomSlideFinder;
		String targetFolderId = props.getSlidesTargetFolderId();
		this.standardModel = slideModel(
				props.getSummaryTableObjectIds(), props.getSummarySlideObjectIds(),
				props.getResultsSlideObjectIds(), props.getTacticSlideObjectIds(),
				props.getBreakdownMasterSlideObjectIds(), props.getThoughtsMasterSlideObjectId(),
				props.getTacticMasterSlideObjectId());
		MpoDeckSlideIds mpoEoc = mpoDeckPolicy.eocSlideIds();
		this.mpoEocModel = slideModel(
				mpoEoc.getSummaryTableObjectIds(), mpoEoc.getSummarySlideObjectIds(),
				mpoEoc.getResultsSlideObjectIds(), Map.of(),
				mpoEoc.getBreakdownMasterSlideObjectIds(), mpoEoc.getThoughtsMasterSlideObjectId(),
				mpoEoc.getTacticMasterSlideObjectId());
		this.eomDropSlideObjectIds = normalizeSlideIds(eomDeckPolicy.dropSlideObjectIds());
		this.eomDropSlideTitles = normalizeTitles(eomDeckPolicy.dropSlideTitles());
		this.eomDropElementLabels = normalizeTitles(eomDeckPolicy.dropElementLabels());
		this.eomRelayoutGrowLabel = normalizeTitle(eomDeckPolicy.relayoutGrowLabel());
		this.eomRelayoutAnchorLabel = normalizeTitle(eomDeckPolicy.relayoutAnchorLabel());
		this.slideBlockLocator = slideBlockLocator;
		this.tacticSlideRelayout = tacticSlideRelayout;
		this.deviceTableCompactor = deviceTableCompactor;
		this.creativeTableCompactor = creativeTableCompactor;
		this.breakdownSlideNaming = breakdownSlideNaming;
		this.summaryTableRowTrimmer = summaryTableRowTrimmer;
		this.deckMarkup = deckMarkup;
		this.driveSharer = driveSharer;
		this.retrier = retrier;
		this.shareRecipients = shareRecipients;
		this.creds = creds;
		this.slides = new Slides.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.drive = new Drive.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.slidesTemplateId = slidesTemplateId;
		this.targetFolderId = targetFolderId == null ? "" : targetFolderId.trim();
		// Which template this instance will actually clone, and under which slide model. Logged at startup
		// because the answer is otherwise invisible: a deck built from the wrong template looks like a code
		// problem, while it is nearly always an environment variable that never reached the process (set but
		// not restarted, or set on the other environment). One line here settles that in a glance.
		log.info("[slides] template={} eomTemplate={} mpoTemplates=[{}] tacticModel={} tacticMaster={} "
						+ "breakdownMasters={} mpoEocTacticMaster={} mpoEocBreakdownMasters={}",
				this.slidesTemplateId,
				eomDeckPolicy.describeTemplate(),
				mpoDeckPolicy.describeTemplates(),
				standardModel.masterTacticModel() ? "master" : "legacy-28-slots",
				standardModel.masterTacticModel() ? standardModel.tacticMasterId() : UNSET_MARKER,
				standardModel.breakdownMasterIds().size(),
				mpoEocModel.masterTacticModel() ? mpoEocModel.tacticMasterId() : UNSET_MARKER,
				mpoEocModel.breakdownMasterIds().size());
	}

	/**
	 * Builds a slide model from configured ids, normalizing each one the same way for every template: maps
	 * never {@code null}, unknown breakdown codes and blank ids dropped, slide ids trimmed of a pasted
	 * {@code id.} URL prefix.
	 *
	 * @param summaryTables  group → summary table id (may be {@code null})
	 * @param summarySlides  group → summary slide id (may be {@code null})
	 * @param resultsSlides  group → results slide id (may be {@code null})
	 * @param tacticSlides   tactic → drawn tactic slide id, legacy model only (may be {@code null})
	 * @param breakdownMasters breakdown wire code → master slide id (may be {@code null})
	 * @param thoughtsMaster the thoughts master slide id (may be {@code null})
	 * @param tacticMaster   the main tactic master slide id (may be {@code null})
	 * @return the normalized model
	 */
	SlideModel slideModel(
			Map<Integer, String> summaryTables, Map<Integer, String> summarySlides,
			Map<Integer, String> resultsSlides, Map<Integer, String> tacticSlides,
			Map<String, String> breakdownMasters, String thoughtsMaster, String tacticMaster) {
		return new SlideModel(
				summaryTables == null ? Map.of() : summaryTables,
				summarySlides == null ? Map.of() : summarySlides,
				resultsSlides == null ? Map.of() : resultsSlides,
				tacticSlides == null ? Map.of() : tacticSlides,
				resolveBreakdownMasterIds(breakdownMasters),
				thoughtsMaster == null ? "" : thoughtsMaster.trim(),
				normalizeSlideId(tacticMaster));
	}

	/**
	 * Picks the slide ids a deck's slide passes work against: the EOC MPO template's own ids when the deck
	 * was cloned from it, the standard ids otherwise (including an MPO request that fell back to the
	 * standard template, which is then a standard deck).
	 *
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant requested deck design, may be {@code null} (read as standard)
	 * @return the slide model for that deck
	 */
	SlideModel slideModelFor(String reportType, DeckTemplate templateVariant) {
		return mpoDeckPolicy.usesEocSlides(templateVariant, reportType) ? mpoEocModel : standardModel;
	}

	/**
	 * Resolves which template deck to clone. The standard design clones the EOM deck when
	 * {@code reportType} is {@code "EOM"} and an EOM template id is configured, otherwise the EOC
	 * (default) deck; the MPO design clones the MPO template configured for the report type, falling back
	 * to that standard choice when none is.
	 *
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant requested deck design, may be {@code null} (read as standard)
	 * @return the template id to clone
	 */
	String templateIdFor(String reportType, DeckTemplate templateVariant) {
		return mpoDeckPolicy.templateIdOr(
				templateVariant, reportType, eomDeckPolicy.templateIdOr(reportType, slidesTemplateId));
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public boolean carriesTacticThoughts(String reportType, DeckTemplate templateVariant) {
		return eomDeckPolicy.appliesTo(reportType)
				|| !slideModelFor(reportType, templateVariant).thoughtsMasterId().isBlank();
	}

	@Override
	public boolean carriesMpoNarrative(String reportType, DeckTemplate templateVariant) {
		return mpoDeckPolicy.usesMpoTemplate(templateVariant, reportType);
	}

	@Override
	public String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			DeckTemplate templateVariant, String userGoogleAccessToken) {
		String templateId = templateIdFor(reportType, templateVariant);
		// Which template a run cloned is otherwise invisible: the artifact looks plausible either way, and a
		// stale-or-swapped id has already cost one debugging session. Logged before the copy, so a failed
		// copy still names the id it tried.
		log.info("[slides] job {} cloning {} {} template {}", jobId, reportType, templateVariant, templateId);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Drive driveClient = asUser ? buildDrive(userGoogleAccessToken) : drive;
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			File copy = new File().setName(fileName);
			if (!targetFolderId.isEmpty()) {
				copy.setParents(List.of(targetFolderId));
			} else if (asUser) {
				// Drive's files.copy inherits the source's parent when no parent
				// is given, so the deck would land in the template's (shared)
				// folder and show up under the user's "Shared with me" instead of
				// "My Drive". Force the user's own My Drive root so the deck is
				// both owned by — and located in — the signed-in user's drive.
				copy.setParents(List.of("root"));
			}
			File copied = retrier.execute(
					driveClient.files().copy(templateId, copy)
							.setFields("id,webViewLink")
							.setSupportsAllDrives(true),
					"createDeck copy of " + templateId);
			String newId = copied.getId();

			List<Request> requests = new ArrayList<>(placeholderMap.size());
			for (Map.Entry<String, String> e : placeholderMap.entrySet()) {
				// Template tokens are double-brace {{...}} — the key is already the full token.
				String token = e.getKey();
				requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
						.setContainsText(new SubstringMatchCriteria().setText(token).setMatchCase(true))
						.setReplaceText(e.getValue() == null ? "" : e.getValue())));
			}
			if (!requests.isEmpty()) {
				// Fill the freshly-copied deck before granting access. Sharing is a Drive ACL write on the
				// same file, and issuing it concurrently with a large content batchUpdate lets Google abort
				// the write with 409 ABORTED (seen on long decks, whose batchUpdate runs longer). Each chunk
				// is retried on transient conflicts/rate limits — Slides batchUpdate is atomic, so a retry
				// never double-applies. Chunking also shortens each write, further reducing the 409 window.
				executeInChunks(slidesClient, newId, requests, "createDeck batchUpdate for " + newId);
			}

			// Grant standing access to the configured recipients (e.g. an admin owner) so decks created in a
			// user's own My Drive remain reachable — after the fill, to keep the ACL write off the content write.
			driveSharer.shareWith(driveClient, newId, shareRecipients.resolve());
			return "https://docs.google.com/presentation/d/" + newId + "/edit";
		} catch (IOException ex) {
			log.error("[slides] createDeck failed for job {}", jobId, ex);
			throw new GoogleApiException("Google Slides deck creation failed: " + ex.getMessage());
		}
	}

	@Override
	public void trimTactics(
			String presentationId, int tacticCount, String reportType, DeckTemplate templateVariant,
			String userGoogleAccessToken) {
		if (eomDeck(reportType, templateVariant)) {
			trimEomDashboardSlides(presentationId, tacticCount, userGoogleAccessToken);
			return;
		}
		if (tacticCount >= MAX_TACTICS) {
			return;
		}
		SlideModel model = slideModelFor(reportType, templateVariant);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			List<Request> requests = trimRequests(model, tacticCount);
			// The last partial group's surplus table rows are located by reading the deck rather than by
			// assuming fixed row indices: how many header rows the template keeps inside the table differs
			// per template, and guessing wrong deletes the Totals row instead of a tactic row.
			requests.addAll(surplusRowRequests(model, slidesClient, presentationId, tacticCount));
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests, "trimTactics batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] trimTactics failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides trimTactics failed: " + ex.getMessage());
		}
	}

	/**
	 * Applies the given batchUpdate requests to a deck in fixed-size chunks of at most
	 * {@link #BATCH_UPDATE_CHUNK_SIZE}, each sent as its own {@code batchUpdate} and retried
	 * independently via {@link GoogleRequestRetrier}. Mirrors {@code RealSheetDeckProvider#executeInChunks}:
	 * a single batchUpdate carrying every {@code replaceAllText} (~800 for a full 28-tactic report) risks
	 * repeated 500/503 {@code backendError}s from Slides; chunking keeps each request small. Chunks are
	 * sent in list order, so the trim's bottom-up {@code deleteTableRow} sequence stays valid across
	 * boundaries, and replaceAllText targets disjoint tokens — chunking never changes the result.
	 *
	 * @param slidesClient   the authenticated Slides client
	 * @param presentationId the deck to update
	 * @param requests       the batchUpdate requests to apply, in list order
	 * @param description    short context used in retry log lines
	 * @throws IOException when a chunk fails with a non-retryable error or exhausts all attempts
	 */
	void executeInChunks(Slides slidesClient, String presentationId, List<Request> requests, String description)
			throws IOException {
		int total = requests.size();
		int chunks = (total + BATCH_UPDATE_CHUNK_SIZE - 1) / BATCH_UPDATE_CHUNK_SIZE;
		for (int start = 0, index = 1; start < total; start += BATCH_UPDATE_CHUNK_SIZE, index++) {
			int end = Math.min(start + BATCH_UPDATE_CHUNK_SIZE, total);
			List<Request> chunk = new ArrayList<>(requests.subList(start, end));
			retrier.execute(
					slidesClient.presentations()
							.batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(chunk)),
					description + " (chunk " + index + "/" + chunks + ")");
		}
	}

	/**
	 * Builds the whole-object delete requests for a deck trimmed to {@code tacticCount} tactics: the surplus
	 * per-tactic detail slides and the surplus "Our results" and summary group slides. Tactics are grouped
	 * 7‑per‑group (group 1 → tactics 1–7, group 2 → 8–14, …), so {@code groups = ceil(tacticCount / 7)} and
	 * every group above that is empty and deleted whole. The last, partial group's unused table rows are not
	 * handled here — they need the live table, see {@link #surplusRowRequests}. Requests are emitted only for
	 * configured (non-blank) object ids, so an unconfigured deck degrades to a safe no-op.
	 *
	 * @param model       the slide ids of the template the deck was cloned from
	 * @param tacticCount number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @return the ordered list of delete requests (empty when nothing is configured to trim)
	 */
	List<Request> trimRequests(SlideModel model, int tacticCount) {
		int groups = (tacticCount + TACTICS_PER_GROUP - 1) / TACTICS_PER_GROUP;

		List<Request> requests = new ArrayList<>();
		// Surplus per-tactic detail slides — legacy model only: under the master model the deck is built with
		// exactly as many tactic slides as there are tactics, so there is nothing to delete.
		if (!model.masterTacticModel()) {
			for (int t = tacticCount + 1; t <= MAX_TACTICS; t++) {
				addDeleteObject(requests, model.tacticSlideObjectIds().get(t));
			}
		}
		// Surplus "Our results" and summary slides for empty groups.
		for (int g = groups + 1; g <= GROUP_COUNT; g++) {
			addDeleteObject(requests, model.resultsSlideObjectIds().get(g));
			addDeleteObject(requests, model.summarySlideObjectIds().get(g));
		}
		return requests;
	}

	/**
	 * Builds the {@code deleteTableRow} requests for the unused tactic rows of the last, partial group's
	 * summary table. Needs the live deck: the rows are located by reading the table (Totals row last, the
	 * seven tactic rows directly above it) instead of by fixed indices, because a template may or may not
	 * keep its header row inside the table — an assumption that, when wrong, deleted the Totals row and left
	 * a raw {@code {{tactic N}}} row behind.
	 *
	 * <p>Non-fatal by design: a failed read yields no row requests, so the slide deletes still go through and
	 * the deck ships with an untrimmed table rather than not at all.
	 *
	 * @param model          the slide ids of the template the deck was cloned from
	 * @param slidesClient   the authenticated Slides client
	 * @param presentationId the deck being trimmed
	 * @param tacticCount    number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @return the row delete requests, bottom-up; empty when there is nothing (or nothing safe) to delete
	 */
	List<Request> surplusRowRequests(
			SlideModel model, Slides slidesClient, String presentationId, int tacticCount) {
		int groups = (tacticCount + TACTICS_PER_GROUP - 1) / TACTICS_PER_GROUP;
		int usedInLastGroup = tacticCount - (groups - 1) * TACTICS_PER_GROUP;
		String lastTableId = model.summaryTableObjectIds().get(groups);
		if (usedInLastGroup >= TACTICS_PER_GROUP || lastTableId == null || lastTableId.isBlank()) {
			return new ArrayList<>();
		}
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(TABLE_TRIM_FIELDS),
					"trimTactics get " + presentationId);
			return summaryTableRowTrimmer.deleteRowRequests(
					deck.getSlides(), lastTableId, TACTICS_PER_GROUP, usedInLastGroup);
		} catch (IOException ex) {
			log.warn("[slides] trimTactics: reading {} for the summary-table trim failed ({}) - "
					+ "leaving the table untrimmed", presentationId, ex.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * Appends a {@code DeleteObject} request for the given page-element object id, but only when it
	 * is configured (non-blank) — so missing object ids are skipped rather than failing the trim.
	 *
	 * @param requests the accumulating request list
	 * @param objectId the slide/page-element object id to delete (may be {@code null}/blank)
	 */
	void addDeleteObject(List<Request> requests, String objectId) {
		if (objectId != null && !objectId.isBlank()) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
		}
	}

	@Override
	public void addTacticSlides(
			String presentationId, int tacticCount, Map<String, String> placeholderMap, String reportType,
			DeckTemplate templateVariant, String userGoogleAccessToken) {
		if (eomDeck(reportType, templateVariant)) {
			addEomTacticSlides(presentationId, tacticCount, placeholderMap, userGoogleAccessToken);
			return;
		}
		SlideModel model = slideModelFor(reportType, templateVariant);
		if (!model.masterTacticModel()) {
			return;
		}
		Map<String, String> values = placeholderMap == null ? Map.of() : placeholderMap;
		int count = Math.clamp(tacticCount, 1, MAX_TACTICS);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addTacticSlides get " + presentationId);
			List<Request> requests = buildTacticRequests(model, deck.getSlides(), count, values);
			// A configured master that is not in the deck means the deck will ship with no per-tactic slides at
			// all — every tactic loses its main slide, and its breakdowns lose the anchor they are placed after.
			// Raised rather than returned quietly so the caller can turn it into a job warning: silence here is
			// what made a whole run come back without tactic slides and still look successful.
			if (requests.isEmpty()) {
				throw new GoogleApiException("master tactic slide " + model.tacticMasterId()
						+ " was not found in deck " + presentationId
						+ "; no per-tactic slides were built");
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addTacticSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] addTacticSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides addTacticSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the ordered batchUpdate requests that turn the single master tactic slide into one slide per
	 * active tactic: duplicate the master {@code tacticCount} times under the deterministic copy ids
	 * {@link BreakdownSlideNaming#tacticSlideId(int)}, write each copy's {@code n} tokens with that tactic's
	 * value (scoped to the copy), then move the whole run of copies into the master's own position so the
	 * tactic block lands exactly where the template drew it. The duplicates are emitted last tactic first,
	 * which is what leaves them in ascending order in the deck — the order the move request requires.
	 *
	 * <p>Requests are emitted in two ordered phases — all duplicates + token writes, then the single position
	 * move — so no request references a slide an earlier one has not created yet. The master itself is left in
	 * place for {@link #deleteMasterSlides}; the copies sit before it, so deleting it afterwards leaves the
	 * block where the move put it.
	 *
	 * @param model          the slide ids of the template the deck was cloned from
	 * @param slides         the deck's slides in order (from {@code presentations.get}), carrying the master's
	 *                       text for token discovery
	 * @param tacticCount    number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @param placeholderMap resolved token → value pairs; a renumbered token absent from the map is only
	 *                       renumbered
	 * @return the ordered batchUpdate requests, or an empty list when the master is absent from the deck
	 */
	List<Request> buildTacticRequests(
			SlideModel model, List<Page> slides, int tacticCount, Map<String, String> placeholderMap) {
		String tacticMasterId = model.tacticMasterId();
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		int masterIndex = -1;
		Page master = null;
		for (int i = 0; i < slides.size(); i++) {
			if (tacticMasterId.equals(slides.get(i).getObjectId())) {
				masterIndex = i;
				master = slides.get(i);
				break;
			}
		}
		if (master == null) {
			log.warn("[slides] addTacticSlides: master tactic slide {} not in deck — skipping", tacticMasterId);
			return requests;
		}

		// Phase 1: one copy per tactic, each with its own values written into the copy's tokens. The deck's
		// global placeholder pass has already run and will not run again, so a token left merely renumbered
		// here would stay raw in the delivered deck — hence the values map, not a plain renumber.
		//
		// Duplicated from the last tactic down to the first, which is what leaves the copies in ascending
		// tactic order in the deck: every duplicate lands immediately after the master, pushing the previous
		// one further down. Duplicating 1..N instead leaves them reversed (N first), and the position move
		// below then fails the whole batch with "The slides should be in presentation order, with no
		// duplicates" — a 400 that shipped decks with no tactic slides at all.
		Set<String> tokens = extractRenumberableTokens(master);
		for (int n = tacticCount; n >= 1; n--) {
			String copyId = breakdownSlideNaming.tacticSlideId(n);
			requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
					.setObjectId(tacticMasterId)
					.setObjectIds(Map.of(tacticMasterId, copyId))));
			emitRenumberedTokens(requests, copyId, n, tokens, placeholderMap);
		}

		// Phase 2: the copies now sit directly after the master in ascending tactic order — the order this
		// list must be in — so moving them to the master's index puts the block exactly where the template
		// had it, master last.
		List<String> copyIds = new ArrayList<>(tacticCount);
		for (int n = 1; n <= tacticCount; n++) {
			copyIds.add(breakdownSlideNaming.tacticSlideId(n));
		}
		requests.add(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
				.setSlideObjectIds(copyIds)
				.setInsertionIndex(masterIndex)));
		return requests;
	}

	/**
	 * Resolves the object id of a tactic's main slide in the deck: the deterministic id of the master's copy
	 * under the master model, or the configured per-slot template id under the legacy model. The breakdown
	 * step anchors each tactic's copies after this slide.
	 *
	 * @param model     the slide ids of the template the deck was cloned from
	 * @param tacticNum the 1-based tactic number
	 * @return the main slide object id, or {@code null} when the legacy model has no slot configured
	 */
	String mainTacticSlideId(SlideModel model, int tacticNum) {
		return model.masterTacticModel()
				? breakdownSlideNaming.tacticSlideId(tacticNum)
				: model.tacticSlideObjectIds().get(tacticNum);
	}

	@Override
	public void addBreakdownSlides(
			String presentationId, Map<Integer, Set<DeckBreakdown>> enabledByTactic,
			Map<String, String> breakdownValues, Set<Integer> thoughtsTactics, String reportType,
			DeckTemplate templateVariant, String userGoogleAccessToken) {
		if (eomDeck(reportType, templateVariant)) {
			addEomBreakdownSlides(presentationId, enabledByTactic, breakdownValues, thoughtsTactics,
					userGoogleAccessToken);
			return;
		}
		SlideModel model = slideModelFor(reportType, templateVariant);
		if (enabledByTactic == null || enabledByTactic.isEmpty() || model.breakdownMasterIds().isEmpty()) {
			return;
		}
		Map<String, String> values = breakdownValues == null ? Map.of() : breakdownValues;
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addBreakdownSlides get " + presentationId);
			List<Request> requests =
					buildBreakdownRequests(
							model, deck.getSlides(), model.breakdownMasterIds(), enabledByTactic, values,
							thoughtsTactics);
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addBreakdownSlides batchUpdate for " + presentationId);
			compactDeviceTables(slidesClient, presentationId, enabledByTactic);
		} catch (IOException ex) {
			log.error("[slides] addBreakdownSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides addBreakdownSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the ordered batchUpdate requests that insert the selected per-tactic breakdown slides into
	 * an already-built deck: duplicate each master, renumber its {@code n} tokens to the tactic number
	 * (scoped to the copy), then position each tactic's copies right after its main slide. Requests are
	 * emitted in two ordered phases — all duplicates+renumbers, then all position moves — so a later
	 * request never references a slide an earlier one has not yet created. The masters themselves are left
	 * in place for {@link #deleteMasterSlides} to remove in a separate, unconditional pass.
	 *
	 * <p>Only tactics whose main slide is present in the deck and that enable at least one breakdown with
	 * a configured, in-deck master are processed; everything else is skipped so the method degrades to a
	 * safe no-op. The position math assumes the master slides (and therefore the freshly duplicated
	 * copies, which land next to their masters) sit after every tactic slide — the deck's fixed layout —
	 * so duplicating never shifts a tactic slide's index. Tactics are positioned in ascending main-slide
	 * order while accumulating the number of already-inserted copies, keeping every insertion index valid
	 * against the live arrangement.
	 *
	 * @param model            the slide ids of the template the deck was cloned from
	 * @param slides           the deck's slides in order (from {@code presentations.get}), carrying master
	 *                         text for token discovery
	 * @param masterIds        configured master slide object id per breakdown type
	 * @param enabledByTactic  1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues  renumbered token → final value; a token found here is written straight to
	 *                         its value, one found nowhere is only renumbered
	 * @param thoughtsTactics  tactics that get a "Thoughts on tactic performance" copy (those past the
	 *                         service's gate)
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildBreakdownRequests(
			SlideModel model, List<Page> slides, Map<DeckBreakdown, String> masterIds,
			Map<Integer, Set<DeckBreakdown>> enabledByTactic, Map<String, String> breakdownValues,
			Set<Integer> thoughtsTactics) {
		return buildBreakdownRequests(model, slides, masterIds, enabledByTactic, breakdownValues, Map.of(),
				thoughtsTactics);
	}

	/**
	 * Same as {@link #buildBreakdownRequests(SlideModel, List, Map, Map, Map, Set)}, with the anchor slide each
	 * tactic's copies are placed after supplied explicitly. An EOM deck builds two master slides per tactic,
	 * so its anchor is the second of them rather than the single slide
	 * {@link #mainTacticSlideId(SlideModel, int)} names.
	 *
	 * @param model             the slide ids of the template the deck was cloned from
	 * @param slides            the deck's slides in order (from {@code presentations.get})
	 * @param masterIds         master slide object id per breakdown type
	 * @param enabledByTactic   1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues   renumbered token → final value; a token absent from the map is only renumbered
	 * @param anchorSlideByTactic 1-based tactic number → the slide its copies follow; empty falls back to
	 *                          {@link #mainTacticSlideId(SlideModel, int)}
	 * @param thoughtsTactics     tactics that get a "Thoughts on tactic performance" copy (those past the
	 *                            service's gate)
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildBreakdownRequests(
			SlideModel model, List<Page> slides, Map<DeckBreakdown, String> masterIds,
			Map<Integer, Set<DeckBreakdown>> enabledByTactic, Map<String, String> breakdownValues,
			Map<Integer, String> anchorSlideByTactic, Set<Integer> thoughtsTactics) {
		return buildBreakdownRequests(model, slides, masterIds, enabledByTactic, breakdownValues,
				anchorSlideByTactic, model.thoughtsMasterId(), thoughtsTactics);
	}

	/**
	 * Same as {@link #buildBreakdownRequests(SlideModel, List, Map, Map, Map, Map, Set)}, with the thoughts master
	 * supplied explicitly. An EOC deck names that slide by configured object id, while an EOM deck discovers
	 * it by the token it carries, so the id cannot come from the same place for both.
	 *
	 * @param model               the slide ids of the template the deck was cloned from
	 * @param slides              the deck's slides in order (from {@code presentations.get})
	 * @param masterIds           master slide object id per breakdown type
	 * @param enabledByTactic     1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues     renumbered token → final value; a token absent from the map is only renumbered
	 * @param anchorSlideByTactic 1-based tactic number → the slide its copies follow; empty falls back to
	 *                            {@link #mainTacticSlideId(SlideModel, int)}
	 * @param thoughtsMaster      the "Thoughts on tactic performance" master slide id; blank or {@code null}
	 *                            disables that slide as a no-op
	 * @param thoughtsTactics     tactics that get a "Thoughts on tactic performance" copy (those past the
	 *                            service's gate)
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildBreakdownRequests(
			SlideModel model, List<Page> slides, Map<DeckBreakdown, String> masterIds,
			Map<Integer, Set<DeckBreakdown>> enabledByTactic, Map<String, String> breakdownValues,
			Map<Integer, String> anchorSlideByTactic, String thoughtsMaster, Set<Integer> thoughtsTactics) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty() || masterIds.isEmpty() || enabledByTactic.isEmpty()) {
			return requests;
		}

		Map<String, Integer> indexById = new HashMap<>();
		Map<String, Page> pageById = new HashMap<>();
		for (int i = 0; i < slides.size(); i++) {
			Page page = slides.get(i);
			if (page.getObjectId() != null) {
				indexById.put(page.getObjectId(), i);
				pageById.put(page.getObjectId(), page);
			}
		}

		// Qualifying tactics → their enabled breakdown types in declaration (priority) order, and the
		// index of each tactic's main slide (used to order and place the copies).
		Map<Integer, List<DeckBreakdown>> orderedByTactic = new LinkedHashMap<>();
		TreeMap<Integer, Integer> tacticByMainSlideIndex = new TreeMap<>();
		for (Map.Entry<Integer, Set<DeckBreakdown>> entry : enabledByTactic.entrySet()) {
			Integer tacticNum = entry.getKey();
			if (tacticNum == null || entry.getValue() == null || entry.getValue().isEmpty()) {
				continue;
			}
			String mainSlideId = anchorSlideByTactic.getOrDefault(tacticNum, mainTacticSlideId(model, tacticNum));
			if (mainSlideId == null || !indexById.containsKey(mainSlideId)) {
				log.warn("[slides] addBreakdownSlides: no main slide for tactic {} in deck — skipping", tacticNum);
				continue;
			}
			List<DeckBreakdown> types = new ArrayList<>();
			for (DeckBreakdown type : DeckBreakdown.values()) {
				String masterId = masterIds.get(type);
				if (entry.getValue().contains(type) && masterId != null && indexById.containsKey(masterId)) {
					types.add(type);
				}
			}
			if (types.isEmpty()) {
				continue;
			}
			orderedByTactic.put(tacticNum, types);
			tacticByMainSlideIndex.put(indexById.get(mainSlideId), tacticNum);
		}
		if (orderedByTactic.isEmpty()) {
			return requests;
		}

		// Phase 1: duplicate each master, then write each of its n-tokens on the copy — to the token's
		// final value when one is known, otherwise just renumbered. Both are scoped to the copy, which is
		// what stops identical master tokens on different copies from overwriting each other. The deck's
		// global placeholder pass has already run by now and will not run again, so a token left merely
		// renumbered here stays raw in the delivered deck.
		Map<DeckBreakdown, Set<String>> tokensByType = new EnumMap<>(DeckBreakdown.class);
		Map<Integer, List<String>> copyIdsByTactic = new LinkedHashMap<>();
		// The "Thoughts on tactic performance" master is one generic slide (not a breakdown type): it is
		// duplicated once per tactic that passes the shared ">2 breakdowns" gate and appended after that
		// tactic's breakdown copies. A blank or absent master disables it as a safe no-op.
		boolean thoughtsEnabled = thoughtsMaster != null && !thoughtsMaster.isBlank()
				&& indexById.containsKey(thoughtsMaster);
		Set<String> thoughtsTokens = thoughtsEnabled
				? extractRenumberableTokens(pageById.get(thoughtsMaster)) : Set.of();
		for (Map.Entry<Integer, List<DeckBreakdown>> entry : orderedByTactic.entrySet()) {
			int tacticNum = entry.getKey();
			// Keyed by the master's own index in the deck, because a duplicate lands immediately after its
			// master: the copies' order in the deck is their masters' order, whatever order they were created
			// in. The position move below rejects a list that is not in deck order (400 "The slides should be
			// in presentation order"), so the list is derived from the masters' positions rather than from the
			// DeckBreakdown declaration order — which only happens to match the template today.
			TreeMap<Integer, String> copiesByMasterIndex = new TreeMap<>();
			for (DeckBreakdown type : entry.getValue()) {
				String masterId = masterIds.get(type);
				String copyId = breakdownSlideId(type, tacticNum);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(masterId)
						.setObjectIds(Map.of(masterId, copyId))));
				Set<String> tokens = tokensByType.computeIfAbsent(
						type, t -> extractRenumberableTokens(pageById.get(masterId)));
				emitRenumberedTokens(requests, copyId, tacticNum, tokens, breakdownValues);
				copiesByMasterIndex.put(indexById.get(masterId), copyId);
			}
			// The tactic's thoughts copy: its master sits after every breakdown master in the template, so
			// keying it the same way lands it right after the tactic's final breakdown slide.
			if (thoughtsEnabled && thoughtsTactics.contains(tacticNum)) {
				String thoughtsCopyId = breakdownSlideNaming.thoughtsSlideId(tacticNum);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(thoughtsMaster)
						.setObjectIds(Map.of(thoughtsMaster, thoughtsCopyId))));
				emitRenumberedTokens(requests, thoughtsCopyId, tacticNum, thoughtsTokens, breakdownValues);
				copiesByMasterIndex.put(indexById.get(thoughtsMaster), thoughtsCopyId);
			}
			copyIdsByTactic.put(tacticNum, new ArrayList<>(copiesByMasterIndex.values()));
		}

		// Phase 2: place each tactic's copies right after its main slide. Processing in ascending
		// main-slide order and adding the running count of already-inserted copies keeps every
		// insertion index correct against the live arrangement (copies are pulled from after all
		// tactic slides, so earlier tactics' slide indices never shift).
		int inserted = 0;
		for (Map.Entry<Integer, Integer> entry : tacticByMainSlideIndex.entrySet()) {
			int mainSlideIndex = entry.getKey();
			List<String> copyIds = copyIdsByTactic.get(entry.getValue());
			requests.add(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
					.setSlideObjectIds(new ArrayList<>(copyIds))
					.setInsertionIndex(mainSlideIndex + 1 + inserted)));
			inserted += copyIds.size();
		}

		// The master slides are intentionally left in place here: {@link #deleteMasterSlides} removes them
		// in a separate, unconditional pass, so masters are cleaned even when no breakdowns were selected
		// (and this method returned early).
		return requests;
	}

	@Override
	public List<String> dashUnresolvedTokens(
			String presentationId, String reportType, DeckTemplate templateVariant, String userGoogleAccessToken) {
		if (!eomDeck(reportType, templateVariant)) {
			return List.of();
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(DASH_FIELDS),
					"dashUnresolvedTokens get " + presentationId);
			List<String> tokens = unresolvedTokens(deck.getSlides());
			if (tokens.isEmpty()) {
				return tokens;
			}
			log.warn("[slides] {} unresolved token(s) left in deck {} — dashing them: {}",
					tokens.size(), presentationId, tokens);
			List<Request> requests = new ArrayList<>(tokens.size());
			for (String token : tokens) {
				requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
						.setContainsText(new SubstringMatchCriteria().setText(token).setMatchCase(true))
						.setReplaceText(DASH)));
			}
			executeInChunks(slidesClient, presentationId, requests,
					"dashUnresolvedTokens batchUpdate for " + presentationId);
			return tokens;
		} catch (IOException ex) {
			// Non-fatal by design: the deck is already built and delivered, and a failed sweep leaves it
			// exactly as it would have been without this pass.
			log.warn("[slides] dashUnresolvedTokens failed for {} (non-fatal): {}",
					presentationId, ex.getMessage());
			return List.of();
		}
	}

	@Override
	public void applyDeckMarkup(String presentationId, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BOLD_MARKUP_FIELDS),
					"applyDeckMarkup get " + presentationId);
			List<Request> requests = deckMarkup.deckMarkupRequests(deck.getSlides());
			if (!requests.isEmpty()) {
				executeInChunks(slidesClient, presentationId, requests,
						"applyDeckMarkup batchUpdate for " + presentationId);
			}
		} catch (IOException ex) {
			// Non-fatal: without this pass the deck is complete, only the emphasis markers stay visible.
			log.warn("[slides] applyDeckMarkup failed for {} (non-fatal): {}", presentationId, ex.getMessage());
		}
	}

	@Override
	public void fillPaidSlides(String presentationId, PaidDeckFill fill, String userGoogleAccessToken) {
		if (fill == null || fill.isEmpty()) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(PAID_FIELDS),
					"fillPaidSlides get " + presentationId);
			List<Request> requests = paidSlidesWriter.requests(deck.getSlides(), fill);
			if (!requests.isEmpty()) {
				executeInChunks(slidesClient, presentationId, requests,
						"fillPaidSlides batchUpdate for " + presentationId);
			}
		} catch (IOException ex) {
			log.error("[slides] fillPaidSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides fillPaidSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Collects every {@code {{…}}} token still present in a finished deck, deduplicated and in deck order.
	 *
	 * @param slides the deck's slides in order, from {@code presentations.get}
	 * @return the tokens still standing, empty when the deck is fully filled
	 */
	List<String> unresolvedTokens(List<Page> slides) {
		Set<String> tokens = new LinkedHashSet<>();
		if (slides == null) {
			return List.of();
		}
		for (Page page : slides) {
			tokens.addAll(extractRenumberableTokens(page));
			collectGroupTokens(page.getPageElements(), tokens);
		}
		return new ArrayList<>(tokens);
	}

	/**
	 * Adds the tokens of every shape inside a group, at any depth. {@link #extractRenumberableTokens} reads only
	 * top-level shapes and tables, which left a token in a grouped shape (the EOM cover's headline figures) out of
	 * the final sweep, so it shipped raw.
	 *
	 * @param elements a slide's or a group's page elements (may be {@code null})
	 * @param tokens   the accumulating set of distinct tokens
	 */
	void collectGroupTokens(List<PageElement> elements, Set<String> tokens) {
		if (elements == null) {
			return;
		}
		for (PageElement element : elements) {
			if (element.getElementGroup() == null || element.getElementGroup().getChildren() == null) {
				continue;
			}
			for (PageElement child : element.getElementGroup().getChildren()) {
				if (child.getShape() != null) {
					collectTokens(child.getShape().getText(), tokens);
				}
			}
			collectGroupTokens(element.getElementGroup().getChildren(), tokens);
		}
	}

	@Override
	public int countSlides(String presentationId, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			// Object ids only: the count is all that is wanted and pulling page elements would ship the
			// whole deck's content back for a single integer.
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(MASTER_FIELDS),
					"countSlides get " + presentationId);
			return deck.getSlides() == null ? 0 : deck.getSlides().size();
		} catch (IOException ex) {
			// Measuring a finished deck is bookkeeping, not delivery: a report that shipped must not be
			// reported as failed because its slide count could not be read.
			log.warn("[slides] countSlides failed for {}: {}", presentationId, ex.getMessage());
			return 0;
		}
	}


	/**
	 * Whether this deck build follows the EOM model: an EOM report whose own template is switched on, or one
	 * cloned from the EOM MPO template — which carries the same token-discovered slide model whatever the
	 * standard EOM switch says. The EOC path is left untouched for every other case, which is what keeps an
	 * EOM change off the EOC deck.
	 *
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant requested deck design, may be {@code null} (read as standard)
	 * @return {@code true} when the EOM slide model applies
	 */
	boolean eomDeck(String reportType, DeckTemplate templateVariant) {
		return eomDeckPolicy.appliesTo(reportType)
				&& (eomDeckPolicy.hasOwnTemplate() || mpoDeckPolicy.usesMpoTemplate(templateVariant, reportType));
	}

	/**
	 * Trims the EOM dashboards down to the tactics the campaign actually has.
	 *
	 * <p>Two things are surplus. Whole slides: the pacing-dashboard and performance-vs-plan slides are each
	 * drawn for a fixed block of seven tactics, so a three-tactic campaign leaves six of them showing raw
	 * {@code {{tactic 8 …}}} tokens. And, on the one block the tactic count lands inside, the table rows
	 * above that count — a three-tactic campaign keeps the 1–7 slides but only three of their seven rows.
	 * Both are removed in one batch: the slides deleted and the rows deleted belong to different objects,
	 * so the order between them does not matter.
	 *
	 * <p>Nothing else is trimmed: an EOM deck builds its tactic slides from masters, so it has no surplus
	 * drawn slides to remove.
	 *
	 * @param presentationId        the deck to trim
	 * @param tacticCount           number of real tactics in the campaign
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void trimEomDashboardSlides(String presentationId, int tacticCount, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(EOM_TRIM_FIELDS),
					"trimEomDashboardSlides get " + presentationId);
			List<String> surplus = eomSlideFinder.surplusTacticSlideIds(
					deck.getSlides(), tacticCount, TACTICS_PER_GROUP);
			List<Request> requests = new ArrayList<>();
			for (String objectId : surplus) {
				addDeleteObject(requests, objectId);
			}
			requests.addAll(eomDashboardRowRequests(deck.getSlides(), tacticCount));
			if (requests.isEmpty()) {
				return;
			}
			log.info("[slides] EOM trim: dropping {} dashboard slide(s) above tactic {} and {} surplus "
					+ "table row(s)", surplus.size(), tacticCount, requests.size() - surplus.size());
			executeInChunks(slidesClient, presentationId, requests,
					"trimEomDashboardSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] trimEomDashboardSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides trimEomDashboardSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the row-delete requests for the dashboard tables of the last, partly filled block of tactics.
	 *
	 * <p>The tables are found by the tokens they print rather than by configured object id — the same reason
	 * every other EOM lookup is — and then handed to the shared row trimmer, which locates the tactic rows by
	 * reading the table (totals row last, the block's tactic rows directly above it) instead of assuming
	 * fixed indices. Both dashboards of the block are trimmed, whether or not the table carries a totals row.
	 *
	 * @param pages       the deck's slides, from {@code presentations.get} with {@link #EOM_TRIM_FIELDS}
	 * @param tacticCount number of real tactics in the campaign
	 * @return the row-delete requests, bottom-up per table; empty when the last block is full
	 */
	List<Request> eomDashboardRowRequests(List<Page> pages, int tacticCount) {
		List<Request> requests = new ArrayList<>();
		int usedInLastBlock = tacticCount % TACTICS_PER_GROUP;
		if (usedInLastBlock == 0) {
			return requests;
		}
		for (String tableId : eomSlideFinder.partialBlockTableIds(pages, tacticCount, TACTICS_PER_GROUP)) {
			requests.addAll(summaryTableRowTrimmer.deleteRowRequests(
					pages, tableId, TACTICS_PER_GROUP, usedInLastBlock));
		}
		return requests;
	}

	/**
	 * Builds an EOM deck's per-tactic slides: every master tactic slide found in the deck is duplicated
	 * once per tactic, and each tactic's copies are placed together, in the masters' own template order.
	 * The EOM template carries two such masters, so tactic 3 ends up as the pair
	 * {@code (EOC-style slide, channel slide)} — and its breakdown slides follow the pair, never split it.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param tacticCount           number of real tactics
	 * @param placeholderMap        resolved token → value pairs
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void addEomTacticSlides(
			String presentationId, int tacticCount, Map<String, String> placeholderMap,
			String userGoogleAccessToken) {
		Map<String, String> values = placeholderMap == null ? Map.of() : placeholderMap;
		int count = Math.clamp(tacticCount, 1, MAX_TACTICS);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addEomTacticSlides get " + presentationId);
			List<Request> requests = buildEomTacticRequests(deck.getSlides(), count, values);
			// Same reasoning as the EOC master model: a deck with no tactic slides at all must be reported,
			// not returned quietly as a success.
			if (requests.isEmpty()) {
				throw new GoogleApiException("no EOM master tactic slide found in deck " + presentationId
								+ "; no per-tactic slides were built");
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addEomTacticSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] addEomTacticSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides addEomTacticSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the ordered batchUpdate requests behind {@link #addEomTacticSlides}: duplicate every master
	 * once per tactic (last tactic first, so the copies end up in ascending order behind their master),
	 * write each copy's {@code n} tokens with that tactic's values scoped to the copy, then move each
	 * tactic's copies as one block to where the first master sits.
	 *
	 * <p>The move list for a tactic is {@code (master 0's copy, master 1's copy, …)}, which is also their
	 * current order in the deck — every copy of master 0 precedes master 1 and therefore all of its copies
	 * — because {@code updateSlidesPosition} rejects a list that is not in presentation order. Blocks are
	 * moved in ascending tactic order while accumulating how many copies were already inserted, the same
	 * arithmetic the breakdown pass uses.
	 *
	 * @param slides         the deck's slides in order, from {@code presentations.get}
	 * @param tacticCount    number of real tactics (already clamped by the caller)
	 * @param placeholderMap resolved token → value pairs; a renumbered token absent from the map is only
	 *                       renumbered
	 * @return the ordered batchUpdate requests, or an empty list when the deck carries no master
	 */
	List<Request> buildEomTacticRequests(List<Page> slides, int tacticCount, Map<String, String> placeholderMap) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		List<String> masterIds = eomSlideFinder.tacticMasterSlideIds(slides);
		if (masterIds.isEmpty()) {
			log.warn("[slides] addEomTacticSlides: no master tactic slide in deck — skipping");
			return requests;
		}
		Map<String, Page> pageById = new HashMap<>();
		int firstMasterIndex = -1;
		for (int i = 0; i < slides.size(); i++) {
			Page page = slides.get(i);
			if (page.getObjectId() == null) {
				continue;
			}
			pageById.put(page.getObjectId(), page);
			if (firstMasterIndex < 0 && masterIds.contains(page.getObjectId())) {
				firstMasterIndex = i;
			}
		}

		// Phase 1: duplicate + fill. Per master, from the last tactic down to the first, so the copies sit
		// in ascending tactic order behind their master (the order the position move below requires).
		for (int ordinal = 0; ordinal < masterIds.size(); ordinal++) {
			String masterId = masterIds.get(ordinal);
			Set<String> tokens = extractRenumberableTokens(pageById.get(masterId));
			for (int n = tacticCount; n >= 1; n--) {
				String copyId = breakdownSlideNaming.eomTacticSlideId(ordinal, n);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(masterId)
						.setObjectIds(Map.of(masterId, copyId))));
				emitRenumberedTokens(requests, copyId, n, tokens, placeholderMap);
			}
		}

		// Phase 2: each tactic's copies move together to the first master's position, tactic 1 first.
		int inserted = 0;
		for (int n = 1; n <= tacticCount; n++) {
			List<String> block = new ArrayList<>(masterIds.size());
			for (int ordinal = 0; ordinal < masterIds.size(); ordinal++) {
				block.add(breakdownSlideNaming.eomTacticSlideId(ordinal, n));
			}
			requests.add(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
					.setSlideObjectIds(block)
					.setInsertionIndex(firstMasterIndex + inserted)));
			inserted += block.size();
		}
		return requests;
	}

	/**
	 * Inserts an EOM deck's breakdown slides. Masters are found by the tokens they carry rather than by
	 * configured id, and each tactic's copies are anchored after the LAST of that tactic's master copies,
	 * so a tactic reads as one block: its two channel slides first, then its breakdowns.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param enabledByTactic       1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues       renumbered token → value to write
	 * @param thoughtsTactics       tactics that get a "Thoughts on tactic performance" copy (those past the
	 *                              service's gate)
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void addEomBreakdownSlides(
			String presentationId, Map<Integer, Set<DeckBreakdown>> enabledByTactic,
			Map<String, String> breakdownValues, Set<Integer> thoughtsTactics, String userGoogleAccessToken) {
		if (enabledByTactic == null || enabledByTactic.isEmpty()) {
			return;
		}
		Map<String, String> values = breakdownValues == null ? Map.of() : breakdownValues;
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addEomBreakdownSlides get " + presentationId);
			List<Request> requests =
					buildEomBreakdownRequests(deck.getSlides(), enabledByTactic, values, thoughtsTactics);
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addEomBreakdownSlides batchUpdate for " + presentationId);
			compactDeviceTables(slidesClient, presentationId, enabledByTactic);
		} catch (IOException ex) {
			log.error("[slides] addEomBreakdownSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides addEomBreakdownSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Removes, from every filled device breakdown copy, the table rows of devices the tactic never ran on and
	 * closes the gap they leave (see {@link DeviceTableCompactor}). Runs after the copies are filled, because
	 * only then does an absent device read as an all-dash row. Cosmetic, so a failure is logged and the deck
	 * keeps its dashed rows rather than failing the job. The creative copies get the same treatment in the same
	 * read: their table rows without a creative are deleted (see {@link CreativeTableCompactor}).
	 *
	 * @param slidesClient    the authenticated Slides client
	 * @param presentationId  the deck being built
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 */
	void compactDeviceTables(
			Slides slidesClient, String presentationId, Map<Integer, Set<DeckBreakdown>> enabledByTactic) {
		Set<String> deviceSlideIds = new HashSet<>();
		Set<String> creativeSlideIds = new HashSet<>();
		enabledByTactic.forEach((tacticNum, types) -> {
			if (tacticNum != null && types != null && types.contains(DeckBreakdown.DEVICE)) {
				deviceSlideIds.add(breakdownSlideNaming.slideId(DeckBreakdown.DEVICE, tacticNum));
			}
			if (tacticNum != null && types != null && types.contains(DeckBreakdown.CREATIVE)) {
				creativeSlideIds.add(breakdownSlideNaming.slideId(DeckBreakdown.CREATIVE, tacticNum));
			}
		});
		if (deviceSlideIds.isEmpty() && creativeSlideIds.isEmpty()) {
			return;
		}
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(DEVICE_TABLE_FIELDS),
					"compactDeviceTables get " + presentationId);
			if (deck == null || deck.getSlides() == null) {
				return;
			}
			List<Request> requests = new ArrayList<>();
			for (Page slide : deck.getSlides()) {
				if (deviceSlideIds.contains(slide.getObjectId())) {
					requests.addAll(deviceTableCompactor.compactRequests(slide));
				} else if (creativeSlideIds.contains(slide.getObjectId())) {
					requests.addAll(creativeTableCompactor.compactRequests(slide));
				}
			}
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"compactDeviceTables batchUpdate for " + presentationId);
			log.info("[slides] compactDeviceTables: {} request(s) dropped absent-device rows in {}",
					requests.size(), presentationId);
		} catch (IOException ex) {
			log.warn("[slides] compactDeviceTables failed for {} (non-fatal, dashed rows stay): {}",
					presentationId, ex.getMessage());
		}
	}

	/**
	 * Builds the EOM breakdown requests: the shared builder, given the deck's discovered breakdown masters
	 * and an anchor per tactic pointing at that tactic's last master copy.
	 *
	 * <p>The thoughts master is discovered here too, rather than read from the EOC configuration, and is
	 * handed to the shared builder so an EOM deck gets the same ">2 breakdowns" gate an EOC deck gets.
	 * Without the gate that slide would be copied for every tactic while its text is written only for the
	 * qualifying ones, and the rest would ship five raw {@code {{thoughts on tactic N performance …}}}
	 * tokens.
	 *
	 * @param slides          the deck's slides in order, from {@code presentations.get}
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues renumbered token → value to write
	 * @param thoughtsTactics tactics that get a "Thoughts on tactic performance" copy (those past the
	 *                        service's gate)
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildEomBreakdownRequests(
			List<Page> slides, Map<Integer, Set<DeckBreakdown>> enabledByTactic,
			Map<String, String> breakdownValues, Set<Integer> thoughtsTactics) {
		Map<DeckBreakdown, String> masterIds = eomSlideFinder.breakdownMasterSlideIds(slides);
		if (masterIds.isEmpty()) {
			return new ArrayList<>();
		}
		int lastOrdinal = Math.max(eomSlideFinder.tacticMasterSlideIds(slides).size() - 1, 0);
		Map<Integer, String> anchors = new LinkedHashMap<>();
		for (Integer tacticNum : enabledByTactic.keySet()) {
			if (tacticNum != null) {
				anchors.put(tacticNum, breakdownSlideNaming.eomTacticSlideId(lastOrdinal, tacticNum));
			}
		}
		return buildBreakdownRequests(standardModel, slides, masterIds, enabledByTactic, breakdownValues, anchors,
				eomSlideFinder.thoughtsMasterSlideId(slides), thoughtsTactics);
	}

	/**
	 * Removes an EOM deck's master slides — both tactic masters and every breakdown master — after the
	 * copies have been made. They are found by their tokens, so a redrawn template needs no config change.
	 *
	 * @param presentationId        the already-built deck to clean
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void deleteEomMasterSlides(String presentationId, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"deleteEomMasterSlides get " + presentationId);
			List<Request> requests = buildEomMasterDeleteRequests(deck.getSlides());
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"deleteEomMasterSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] deleteEomMasterSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides deleteEomMasterSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the delete requests for every EOM master slide present in the deck: the tactic masters, the
	 * breakdown masters and the thoughts master. Only slides still carrying the tactic variable {@code n}
	 * are matched, so the copies — whose tokens were renumbered — are never at risk.
	 *
	 * @param slides the deck's slides in order, from {@code presentations.get}
	 * @return the delete requests, or an empty list when no master is left in the deck
	 */
	List<Request> buildEomMasterDeleteRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		Set<String> masters = new LinkedHashSet<>(eomSlideFinder.tacticMasterSlideIds(slides));
		masters.addAll(eomSlideFinder.breakdownMasterSlideIds(slides).values());
		String thoughtsMaster = eomSlideFinder.thoughtsMasterSlideId(slides);
		if (thoughtsMaster != null) {
			masters.add(thoughtsMaster);
		}
		for (String objectId : masters) {
			addDeleteObject(requests, objectId);
		}
		return requests;
	}

	@Override
	public void deleteMasterSlides(
			String presentationId, String reportType, DeckTemplate templateVariant, String userGoogleAccessToken) {
		if (eomDeck(reportType, templateVariant)) {
			deleteEomMasterSlides(presentationId, userGoogleAccessToken);
			return;
		}
		SlideModel model = slideModelFor(reportType, templateVariant);
		if (!model.hasMasters()) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(MASTER_FIELDS),
					"deleteMasterSlides get " + presentationId);
			List<Request> requests = buildMasterDeleteRequests(model, deck.getSlides());
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"deleteMasterSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] deleteMasterSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides deleteMasterSlides failed: " + ex.getMessage());
		}
	}

	@Override
	public void deleteReportTypeSlides(String presentationId, String reportType, String userGoogleAccessToken) {
		if (!eomDeckPolicy.appliesTo(reportType) || (eomDropSlideObjectIds.isEmpty() && eomDropSlideTitles.isEmpty()
				&& eomDropElementLabels.isEmpty())) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(TITLE_FIELDS),
					"deleteReportTypeSlides get " + presentationId);
			List<Request> requests = buildTitleDeleteRequests(deck.getSlides());
			Set<String> deletedSlideIds = deletedSlideIds(requests);
			List<Request> blockRequests = buildBlockDeleteRequests(deck.getSlides(), deletedSlideIds);
			requests.addAll(blockRequests);
			// Measured on the slides as read, before anything is deleted, and sent after the deletes: the
			// relayout only moves elements the deletes leave in place.
			List<Request> relayoutRequests = blockRequests.isEmpty()
					? List.of() : buildRelayoutRequests(deck.getSlides(), deletedSlideIds);
			requests.addAll(relayoutRequests);
			if (requests.isEmpty()) {
				log.warn("[slides] deleteReportTypeSlides matched none of the {} configured id(s) / {} title(s) / "
						+ "{} block heading(s) in {}", eomDropSlideObjectIds.size(), eomDropSlideTitles.size(),
						eomDropElementLabels.size(), presentationId);
				return;
			}
			log.info("[slides] deleteReportTypeSlides removing {} slide(s) and {} block element(s), moving {} "
					+ "element(s) into the freed space, in {}",
					deletedSlideIds.size(), blockRequests.size(), relayoutRequests.size(), presentationId);
			executeInChunks(slidesClient, presentationId, requests,
					"deleteReportTypeSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] deleteReportTypeSlides failed for {}", presentationId, ex);
			throw new GoogleApiException("Google Slides deleteReportTypeSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the {@code DeleteObject} requests for the slides an EOM deck must drop. A slide is deleted when
	 * its object id is one of the configured ids — the reliable route, since object ids survive the Drive copy
	 * that produced the deck — or, for slides not covered by an id, when its text contains one of the
	 * configured titles. Both the slide text and the configured title are reduced to their letters and digits
	 * before matching, so casing, spacing and punctuation in the template cannot break the fallback. Ids
	 * configured but absent from the deck are skipped rather than failing the batch.
	 *
	 * @param slides the deck's slides in order (from {@code presentations.get} with {@link #TITLE_FIELDS})
	 * @return the delete requests in slide order, or an empty list when no slide matched
	 */
	List<Request> buildTitleDeleteRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		for (Page page : slides) {
			String objectId = page.getObjectId();
			if (objectId == null) {
				continue;
			}
			if (eomDropSlideObjectIds.contains(objectId)) {
				requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
				continue;
			}
			String text = normalizeTitle(slideText(page));
			if (text.isEmpty()) {
				continue;
			}
			for (String title : eomDropSlideTitles) {
				if (text.contains(title)) {
					requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
					break;
				}
			}
		}
		return requests;
	}

	/**
	 * Collects the slide ids the whole-slide delete requests remove.
	 *
	 * @param slideDeletes the delete requests built by {@link #buildTitleDeleteRequests}
	 * @return the ids of the slides being deleted whole
	 */
	Set<String> deletedSlideIds(List<Request> slideDeletes) {
		Set<String> ids = new HashSet<>();
		for (Request request : slideDeletes) {
			if (request.getDeleteObject() != null) {
				ids.add(request.getDeleteObject().getObjectId());
			}
		}
		return ids;
	}

	/**
	 * Builds the {@code DeleteObject} requests for the blocks an EOM deck drops from the slides it keeps: on
	 * every slide not already being deleted, each configured heading's card and everything on that card.
	 *
	 * @param slides          the deck's slides in order (from {@code presentations.get} with {@link #TITLE_FIELDS})
	 * @param deletedSlideIds the slides the same pass already deletes whole, skipped here so no request
	 *                        targets an element of a slide that is gone by the time it runs
	 * @return the element delete requests in slide order, or an empty list when no heading matched
	 */
	List<Request> buildBlockDeleteRequests(List<Page> slides, Set<String> deletedSlideIds) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || eomDropElementLabels.isEmpty()) {
			return requests;
		}
		for (Page page : slides) {
			if (page.getObjectId() == null || deletedSlideIds.contains(page.getObjectId())) {
				continue;
			}
			for (String objectId : slideBlockLocator.blockObjectIds(page, eomDropElementLabels)) {
				requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
			}
		}
		return requests;
	}

	/**
	 * Builds the transform updates that close the gap the block drop leaves on each kept slide: the stat tiles
	 * move into the dropped block's place and the grow card rises into their column (see
	 * {@link TacticSlideRelayout}). A slide without that layout yields no updates.
	 *
	 * @param slides          the deck's slides, read before the drop pass deletes anything
	 * @param deletedSlideIds the slides the same pass deletes whole, skipped here
	 * @return the update requests in slide order, or an empty list when the relayout is off or nothing matched
	 */
	List<Request> buildRelayoutRequests(List<Page> slides, Set<String> deletedSlideIds) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || eomRelayoutGrowLabel.isEmpty() || eomRelayoutAnchorLabel.isEmpty()) {
			log.warn("[slides] relayout off: grow label [{}], anchor label [{}] (blank = off)",
					eomRelayoutGrowLabel, eomRelayoutAnchorLabel);
			return requests;
		}
		for (Page page : slides) {
			if (page.getObjectId() == null || deletedSlideIds.contains(page.getObjectId())) {
				continue;
			}
			requests.addAll(tacticSlideRelayout.relayoutRequests(
					page, eomDropElementLabels, eomRelayoutGrowLabel, eomRelayoutAnchorLabel));
		}
		return requests;
	}

	/**
	 * Concatenates every shape and table-cell text run on a slide into one string, so a title split across
	 * runs (Slides splits on any formatting change) still matches as one phrase.
	 *
	 * @param page the slide to read (may carry no page elements)
	 * @return the slide's text, or an empty string when it carries none
	 */
	String slideText(Page page) {
		StringBuilder joined = new StringBuilder();
		if (page.getPageElements() == null) {
			return "";
		}
		for (PageElement element : page.getPageElements()) {
			if (element.getShape() != null) {
				appendText(element.getShape().getText(), joined);
			}
			Table table = element.getTable();
			if (table != null && table.getTableRows() != null) {
				for (TableRow row : table.getTableRows()) {
					if (row.getTableCells() == null) {
						continue;
					}
					for (TableCell cell : row.getTableCells()) {
						appendText(cell.getText(), joined);
					}
				}
			}
		}
		return joined.toString();
	}

	/**
	 * Appends the text runs of one text container (a shape or a table cell) to the accumulator.
	 *
	 * @param text   the text container (may be {@code null})
	 * @param joined the accumulating slide text
	 */
	void appendText(TextContent text, StringBuilder joined) {
		if (text == null || text.getTextElements() == null) {
			return;
		}
		for (TextElement element : text.getTextElements()) {
			if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
				joined.append(element.getTextRun().getContent());
			}
		}
	}

	/**
	 * Normalizes the configured drop slide ids: blank entries are dropped and the {@code id.} prefix the
	 * Slides editor puts in its {@code #slide=id.…} URL fragment is stripped, so an id pasted straight from
	 * the address bar matches the object id the API reports.
	 *
	 * @param configured the raw configured slide object ids (may be null/empty)
	 * @return the normalized, non-blank object ids
	 */
	Set<String> normalizeSlideIds(List<String> configured) {
		Set<String> normalized = new LinkedHashSet<>();
		if (configured == null) {
			return normalized;
		}
		for (String id : configured) {
			String candidate = normalizeSlideId(id);
			if (!candidate.isEmpty()) {
				normalized.add(candidate);
			}
		}
		return normalized;
	}

	/**
	 * Normalizes one configured slide object id: trims it, treats null as blank, and strips the {@code id.}
	 * prefix the Slides editor puts in its {@code #slide=id.…} URL fragment — so an id pasted straight from
	 * the address bar matches the object id the API reports.
	 *
	 * @param raw the configured slide object id (may be {@code null})
	 * @return the normalized object id, or an empty string when unconfigured
	 */
	String normalizeSlideId(String raw) {
		if (raw == null) {
			return "";
		}
		String candidate = raw.trim();
		if (candidate.startsWith(SLIDE_URL_ID_PREFIX)) {
			candidate = candidate.substring(SLIDE_URL_ID_PREFIX.length());
		}
		return candidate;
	}

	/**
	 * Normalizes the configured drop titles for matching, dropping blank entries.
	 *
	 * @param configured the raw configured title phrases (may be null/empty)
	 * @return the normalized, non-blank titles
	 */
	List<String> normalizeTitles(List<String> configured) {
		List<String> normalized = new ArrayList<>();
		if (configured == null) {
			return normalized;
		}
		for (String title : configured) {
			String candidate = normalizeTitle(title);
			if (!candidate.isEmpty()) {
				normalized.add(candidate);
			}
		}
		return normalized;
	}

	/**
	 * Reduces a title (or a slide's text) to its upper-case letters and digits, so casing, spacing and
	 * punctuation differences between the configured phrase and the template cannot break the match.
	 *
	 * @param value the raw text (may be {@code null})
	 * @return the normalized text, or an empty string when there is nothing to match
	 */
	String normalizeTitle(String value) {
		if (value == null) {
			return "";
		}
		return NON_ALPHANUMERIC.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/**
	 * Builds the {@code DeleteObject} requests that remove every configured master slide actually present in
	 * the deck: the breakdown masters, the thoughts master and the main tactic master. Presence is checked
	 * against the deck's slide object ids so a master configured but absent from this template variant (or
	 * already deleted) is skipped rather than failing the batch. Breakdown masters are de-duplicated and
	 * emitted first, then the thoughts master, then the tactic master.
	 *
	 * @param model  the slide ids of the template the deck was cloned from
	 * @param slides the deck's slides in order (from {@code presentations.get} with {@link #MASTER_FIELDS})
	 * @return the ordered delete requests, or an empty list when no configured master is present
	 */
	List<Request> buildMasterDeleteRequests(SlideModel model, List<Page> slides) {
		String thoughtsMasterId = model.thoughtsMasterId();
		String tacticMasterId = model.tacticMasterId();
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		Set<String> presentIds = new HashSet<>();
		for (Page page : slides) {
			if (page.getObjectId() != null) {
				presentIds.add(page.getObjectId());
			}
		}
		for (String masterId : new LinkedHashSet<>(model.breakdownMasterIds().values())) {
			if (masterId != null && presentIds.contains(masterId)) {
				requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(masterId)));
			}
		}
		if (!thoughtsMasterId.isBlank() && presentIds.contains(thoughtsMasterId)) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(thoughtsMasterId)));
		}
		// The master tactic slide goes the same way: its copies carry the real tactics, so the master itself
		// would ship as a slide full of raw {{tactic n …}} tokens. Deleted here rather than in
		// addTacticSlides so it is cleaned even when that step was skipped or failed.
		if (!tacticMasterId.isBlank() && presentIds.contains(tacticMasterId)) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(tacticMasterId)));
		}
		return requests;
	}

	/**
	 * Emits the copy-scoped {@code replaceAllText} requests that turn a duplicated slide's generic {@code n}
	 * tokens into the tactic's concrete tokens — replaced with the token's final value when one is known in
	 * {@code breakdownValues}, otherwise just renumbered. Scoping every replacement to {@code copyId} is what
	 * stops identical master tokens on sibling copies from overwriting each other. A token that renumbers to
	 * itself (carries no {@code n}) is skipped. Shared by the breakdown copies and the thoughts copy so both
	 * fill their tokens identically.
	 *
	 * @param requests        the request list to append the replacements to
	 * @param copyId          the duplicated slide's object id, used to scope each replacement
	 * @param tacticNum       the 1-based tactic number the copy belongs to
	 * @param tokens          the master's distinct renumberable tokens
	 * @param breakdownValues renumbered token → final value; a token found here is written straight to its value
	 */
	void emitRenumberedTokens(
			List<Request> requests, String copyId, int tacticNum, Set<String> tokens,
			Map<String, String> breakdownValues) {
		for (String token : tokens) {
			String concrete = renumber(token, tacticNum);
			String value = breakdownValues.get(concrete);
			String replacement = value != null ? value : concrete;
			if (replacement.equals(token)) {
				continue;
			}
			emitPercentSuffixReplacements(requests, copyId, token, value);
			requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
					.setContainsText(new SubstringMatchCriteria().setText(token).setMatchCase(true))
					.setReplaceText(replacement)
					.setPageObjectIds(List.of(copyId))));
		}
	}

	/**
	 * Absorbs the master slide's own percent sign typed after a stat-tile token ({@code {{dev_n_ctr}} %})
	 * when the value must not be followed by one: an em-dash ("— %" reads as a broken figure) or a value that
	 * already carries its own sign ("0.58% %"). Emitted before the plain token's replacement, so the
	 * suffixed form is matched first; a slide without the suffix simply matches nothing.
	 *
	 * @param requests the request list to append the replacements to
	 * @param copyId   the duplicated slide's object id, used to scope each replacement
	 * @param token    the master's generic token
	 * @param value    the token's final value, or {@code null} when it is only renumbered
	 */
	void emitPercentSuffixReplacements(List<Request> requests, String copyId, String token, String value) {
		if (value == null) {
			return;
		}
		String trimmed = value.trim();
		if (!trimmed.equals(DASH) && !trimmed.endsWith(PERCENT)) {
			return;
		}
		for (String suffix : PERCENT_SUFFIXES) {
			requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
					.setContainsText(new SubstringMatchCriteria().setText(token + suffix).setMatchCase(true))
					.setReplaceText(value)
					.setPageObjectIds(List.of(copyId))));
		}
	}

	/**
	 * Extracts every distinct {@code {{…}}} token on a master slide, concatenating the text runs within
	 * each shape and table cell first so tokens Google split across runs are reassembled before matching.
	 *
	 * @param master the master slide page (may be {@code null} when not found)
	 * @return the distinct tokens found, in first-seen order (empty when the page has no text)
	 */
	Set<String> extractRenumberableTokens(Page master) {
		Set<String> tokens = new LinkedHashSet<>();
		if (master == null || master.getPageElements() == null) {
			return tokens;
		}
		for (PageElement element : master.getPageElements()) {
			if (element.getShape() != null) {
				collectTokens(element.getShape().getText(), tokens);
			}
			Table table = element.getTable();
			if (table != null && table.getTableRows() != null) {
				for (TableRow row : table.getTableRows()) {
					if (row.getTableCells() == null) {
						continue;
					}
					for (TableCell cell : row.getTableCells()) {
						collectTokens(cell.getText(), tokens);
					}
				}
			}
		}
		return tokens;
	}

	/**
	 * Concatenates the text runs of one text container (a shape or a table cell) and adds any
	 * {@code {{…}}} tokens found to the accumulator. Concatenating first is what lets a token split
	 * across runs still match as one string.
	 *
	 * @param text   the text container (may be {@code null})
	 * @param tokens the accumulating set of distinct tokens
	 */
	void collectTokens(TextContent text, Set<String> tokens) {
		if (text == null || text.getTextElements() == null) {
			return;
		}
		StringBuilder joined = new StringBuilder();
		for (TextElement element : text.getTextElements()) {
			if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
				joined.append(element.getTextRun().getContent());
			}
		}
		Matcher matcher = TOKEN.matcher(joined.toString());
		while (matcher.find()) {
			tokens.add(matcher.group());
		}
	}

	/**
	 * Renumbers the standalone tactic variable {@code n} in a token to the given tactic number, leaving
	 * every other character (including an {@code n} inside a word) untouched.
	 *
	 * @param token     the generic master token (e.g. {@code {{publisher_n.1}}})
	 * @param tacticNum the 1-based tactic number to substitute for {@code n}
	 * @return the renumbered token (e.g. {@code {{publisher_3.1}}}); unchanged when it carries no {@code n}
	 */
	String renumber(String token, int tacticNum) {
		return N_VARIABLE.matcher(token).replaceAll(String.valueOf(tacticNum));
	}

	/**
	 * Builds the deterministic object id for a duplicated breakdown slide, unique per
	 * {@code (breakdown type, tactic)} pair, e.g. {@code bd_tp_3}.
	 *
	 * @param type      the breakdown section
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	String breakdownSlideId(DeckBreakdown type, int tacticNum) {
		return breakdownSlideNaming.slideId(type, tacticNum);
	}

	/**
	 * Resolves the configured {@code code → master slide object id} map into a typed map keyed by
	 * {@link DeckBreakdown}, dropping unknown codes and blank ids so an unconfigured or partially
	 * configured deck degrades to a safe no-op.
	 *
	 * @param configured the raw wire-code → object-id map from configuration (may be null/empty)
	 * @return the typed master ids, keyed by breakdown type
	 */
	Map<DeckBreakdown, String> resolveBreakdownMasterIds(Map<String, String> configured) {
		Map<DeckBreakdown, String> resolved = new EnumMap<>(DeckBreakdown.class);
		if (configured == null) {
			return resolved;
		}
		configured.forEach((code, id) -> {
			if (id != null && !id.isBlank() && code != null && !code.isBlank()) {
				DeckBreakdown type = DeckBreakdown.BY_CODE.get(code.trim().toLowerCase());
				if (type != null) {
					resolved.put(type, id.trim());
				}
			}
		});
		return resolved;
	}

	/**
	 * Builds a Drive client authenticated as the signed-in user via their
	 * short-lived Google OAuth access token (sourced from Clerk). The template
	 * must be readable by that user for the copy to succeed.
	 *
	 * @param accessToken the caller's Google OAuth token, or null/blank for the service account
	 * @return a Drive client bound to that identity
	 */
	Drive buildDrive(String accessToken) {

		return new Drive.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	/**
	 * Builds a Slides client for the signed-in user, or for the service account when the token is
	 * blank.
	 *
	 * @param accessToken the caller's Google OAuth token, or null/blank for the service account
	 * @return a Slides client bound to that identity
	 */
	Slides buildSlides(String accessToken) {

		return new Slides.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	HttpRequestInitializer userInitializer(String accessToken) {

		return creds.withTimeout(new HttpCredentialsAdapter(
				GoogleCredentials.create(new AccessToken(accessToken, null))));
	}
}
