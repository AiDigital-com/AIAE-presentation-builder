package com.aidigital.reportconstructor.externalservices.google.slides;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckTemplate;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckFill;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Google Slides + Drive deck operations, in the Google integration's own terms (the service's
 * {@code SlidesProvider} port is implemented on top of this by {@code bridge.SlidesProviderBridge}).
 * Failures are raised as
 * {@link com.aidigital.reportconstructor.externalservices.google.GoogleApiException}
 * with a message meant for the person who ran the report.
 *
 * <p>Deck generation. The real provider
 * clones a template via Drive and runs {@code batchUpdate.replaceAllText};
 * the stub provider fabricates a static template URL for offline demos.
 *
 * <p>Bean selection is automatic: when {@code GOOGLE_SERVICE_ACCOUNT_JSON} is
 * present at startup the real provider wins via {@code @Primary}; otherwise
 * the stub is the only candidate.
 */
public interface SlidesDeckClient {

	/**
	 * @return true when the provider is talking to the real Google APIs.
	 */
	boolean isLive();

	/**
	 * @param jobId                 orchestration job id used as a correlation suffix
	 * @param fileName              Drive file name to give the cloned deck
	 * @param placeholderMap        resolved {@code {token}} → value pairs to write
	 *                              into the cloned deck
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}); selects which
	 *                              template deck is cloned
	 * @param templateVariant       deck design (standard or MPO); with {@code reportType} selects the
	 *                              template cloned. MPO falls back to the standard template when none
	 *                              is configured for the report type
	 * @param userGoogleAccessToken optional Google OAuth access token for the
	 *                              signed-in user (obtained from Clerk). When
	 *                              non-blank the deck is created in that user's
	 *                              personal Drive; when null/blank the provider
	 *                              falls back to the service account.
	 * @return public Slides URL the UI shows in its "Slides ready" card
	 */
	String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			DeckTemplate templateVariant, String userGoogleAccessToken);

	/**
	 * Removes the template's unused per-tactic slides, surplus summary + "Our
	 * results" group slides, and the last summary table's unused rows when the
	 * campaign has fewer than the template's 28 tactic slots. Without this, the
	 * surplus slides survive showing raw {@code {{tactic N …}}} tokens and empty
	 * chart frames. A no-op when {@code tacticCount >= 28}.
	 *
	 * <p>The per-tactic detail slides are skipped on a template built around a master tactic slide (see
	 * {@link #addTacticSlides}): such a deck is built with exactly as many tactic slides as there are
	 * tactics, so there is nothing surplus to delete. The group slides and summary rows are trimmed either
	 * way.
	 *
	 * @param presentationId        the deck to trim
	 * @param tacticCount           number of real tactics (clamped 1..28)
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}); selects the slide model
	 * @param templateVariant       deck design (standard or MPO); with {@code reportType} selects the slide model
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token;
	 *                              falls back to the service account when blank
	 */
	void trimTactics(
			String presentationId, int tacticCount, String reportType, DeckTemplate templateVariant,
			String userGoogleAccessToken);

	/**
	 * Builds the deck's main tactic slides from a single master slide: the master is duplicated once per
	 * active tactic, each copy's generic {@code n} tokens are written with that tactic's values, and the
	 * copies take the master's own position in the deck (in ascending tactic order). The master is left in
	 * place for {@link #deleteMasterSlides} to remove.
	 *
	 * <p>Replaces the pre-master model, in which the template carried 28 drawn tactic slides and
	 * {@link #trimTactics} deleted the surplus. A no-op when no master is configured, so a legacy template
	 * degrades safely and keeps being trimmed instead. When a master <em>is</em> configured but is not in the
	 * deck, this throws instead of returning quietly: the deck would otherwise ship without a single tactic
	 * slide, which the caller must be able to report.
	 *
	 * <p>Values are written here rather than by the deck's normal placeholder pass for the same reason as
	 * {@link #addBreakdownSlides}: the copies do not exist yet when that pass runs, so a token first spelled
	 * {@code {{tactic 3 imps}}} on a copy would never be seen by it and would ship raw. Every replacement is
	 * scoped to its copy, which is what stops the master's identical tokens on sibling copies from
	 * overwriting each other.
	 *
	 * <p>Must run before {@link #addBreakdownSlides}, which anchors each tactic's breakdown copies after
	 * that tactic's main slide.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param tacticCount           number of real tactics (clamped 1..28 by the caller)
	 * @param placeholderMap        resolved {@code {{token}}} → value pairs; a master token whose renumbered
	 *                              form is absent from the map is only renumbered
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}); selects the slide model
	 * @param templateVariant       deck design (standard or MPO); with {@code reportType} selects the slide model
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void addTacticSlides(
			String presentationId, int tacticCount, Map<String, String> placeholderMap, String reportType,
			DeckTemplate templateVariant, String userGoogleAccessToken);

	/**
	 * Inserts the per-tactic breakdown slides selected on Step 3 into an already-built deck. For each
	 * enabled {@code (tactic, breakdown)} pair the corresponding master slide is duplicated, its generic
	 * {@code n} tokens are renumbered to the tactic number (scoped to the copy so identical master tokens
	 * never overwrite each other across copies), and the copy is positioned immediately after that
	 * tactic's main slide. When a tactic enables several breakdowns they follow the {@link DeckBreakdown}
	 * declaration order (Top Publishers → Creative → Geo → Audience → Device). The master slides are left
	 * in place; {@link #deleteMasterSlides} removes them in a separate, unconditional pass.
	 *
	 * <p>A no-op when no masters are configured, the map is empty, or the deck carries no matching tactic
	 * slides — so an unconfigured deck degrades safely.
	 *
	 * <p>Values are filled here rather than by the deck's normal placeholder pass, because the copies do
	 * not exist yet when that pass runs: the deck is built (and every token replaced) before this method
	 * duplicates the masters, so a token first spelled {@code {{publisher_3.1}}} on a copy would never be
	 * seen by it and would ship raw. For each master token whose renumbered form has an entry in
	 * {@code breakdownValues}, the token is therefore replaced with its final value directly; tokens with
	 * no entry fall back to a plain renumber.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param enabledByTactic       1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues       renumbered token (e.g. {@code {{publisher_3.1}}}) → value to write;
	 *                              tokens absent from the map are only renumbered
	 * @param thoughtsTactics       tactics that get a "Thoughts on tactic performance" copy after their
	 *                              breakdown slides (those passing the service's breakdown-thoughts gate)
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}); selects the slide model
	 * @param templateVariant       deck design (standard or MPO); with {@code reportType} selects the slide model
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void addBreakdownSlides(
			String presentationId, Map<Integer, Set<DeckBreakdown>> enabledByTactic,
			Map<String, String> breakdownValues, Set<Integer> thoughtsTactics, String reportType,
			DeckTemplate templateVariant, String userGoogleAccessToken);

	/**
	 * Removes the breakdown master slides (Top Publishers, Creative, Geo, Audience, Device), the
	 * "Thoughts on tactic performance" master and the main tactic master from an already-built deck. These
	 * are template slides that
	 * must never ship, regardless of whether any breakdown slides were inserted — so unlike
	 * {@link #addBreakdownSlides} this runs unconditionally, after breakdown insertion has duplicated
	 * whatever it needed from the masters. Only masters that are both configured and present in the deck
	 * are deleted, so a deck without masters (or one already cleaned) degrades to a safe no-op.
	 *
	 * @param presentationId        the already-built deck to clean
	 * @param reportType            the deck's report type, which decides the set of configured masters
	 * @param templateVariant       deck design (standard or MPO); a deck cloned from the MPO template has its own
	 *                              masters
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void deleteMasterSlides(
			String presentationId, String reportType, DeckTemplate templateVariant, String userGoogleAccessToken);

	/**
	 * Removes the slides that the given report type must never ship. The EOM deck is a copy of the EOC
	 * one and still carries EOC-only story slides (the frequency &amp; velocity play, the awareness /
	 * market-share slide); those are located by their title text and deleted. A no-op for every other
	 * report type, and for an EOM deck in which none of the configured titles is present — so a template
	 * that already dropped them degrades safely.
	 *
	 * @param presentationId        the already-built deck to clean
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void deleteReportTypeSlides(String presentationId, String reportType, String userGoogleAccessToken);

	/**
	 * Replaces every {@code {{token}}} still standing in a finished deck with a dash, so a token the
	 * pipeline had no value for reads as "no figure" instead of shipping its own name to the client.
	 *
	 * <p>Runs last, once every pass that writes text has finished. Scoped to the decks built from the EOM
	 * template: the deck a token belongs to is the one place that knows whether it was meant to be filled,
	 * and the EOM template is the one still gaining slides — an EOC deck's raw token is a defect to fix in
	 * the template, not something to paper over here.
	 *
	 * @param presentationId        the finished deck to sweep
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant       requested deck design, may be {@code null} (read as standard); an EOM deck
	 *                              cloned from the MPO template is swept like one built from the EOM template
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 * @return the tokens that were dashed, in deck order, so the caller can surface them as job warnings
	 */
	List<String> dashUnresolvedTokens(
			String presentationId, String reportType, DeckTemplate templateVariant, String userGoogleAccessToken);

	/**
	 * Applies the markup the placeholder values carry, once every value is in place: every
	 * {@code **key phrase**} marker becomes bold text (copy with one emphasised phrase, such as the MPO
	 * thoughts slide, because a {@code replaceAllText} value cannot carry a style of its own), and every shape
	 * whose whole text is
	 * {@link com.aidigital.reportconstructor.externalservices.google.deck.DeckMarkers#HIDE_SHAPE}
	 * is removed (an optional chip with nothing to show).
	 *
	 * @param presentationId        the finished deck
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void applyDeckMarkup(String presentationId, String userGoogleAccessToken);

	/**
	 * Finishes an MPO deck's Paid Search / Paid Social section once the token pass has run: writes every marker
	 * table's cells (found by the table's alt-text title, written by row and column with each cell keeping its own
	 * style), deletes the rows a table has no data for, and deletes every slide of a channel without an export (the
	 * slides carrying that channel's tokens or table markers, and its divider, found by title). A table the deck does
	 * not carry is skipped with a warning, so an older template degrades to its sample values.
	 *
	 * @param presentationId        the deck, after {@link #createDeck}
	 * @param fill                  the tables to write and the channels to delete
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void fillPaidSlides(String presentationId, PaidDeckFill fill, String userGoogleAccessToken);

	/**
	 * Counts the slides a finished deck contains. Called once the surplus template slides have been
	 * deleted, so the number is what the client actually receives — which is what the admin
	 * dashboard's saved-hours figure is quoted against.
	 *
	 * @param presentationId        the finished deck to measure
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 * @return the number of slides in the deck, or 0 when the deck cannot be measured
	 */
	int countSlides(String presentationId, String userGoogleAccessToken);

	/**
	 * Tells whether the deck cloned for this report type carries a "Thoughts on tactic performance" slide.
	 * Asked before the per-tactic thoughts copy is paid for: a template without that slide (the MPO-style
	 * EOC template) has nowhere to put it, so the calls would be thrown away.
	 *
	 * <p>An EOM deck finds its thoughts master by the token it carries, which only the cloned deck can
	 * show, so the EOM answer stays {@code true} and its slide pass decides as before.
	 *
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant deck design (standard or MPO), may be {@code null} (read as standard)
	 * @return {@code true} when the deck can carry per-tactic thoughts
	 */
	boolean carriesTacticThoughts(String reportType, DeckTemplate templateVariant);

	/**
	 * Tells whether the deck cloned for this report carries the MPO-only narrative slots (HHI, the two
	 * proposal cards, the funnel slide, the recommendations headline). Asked before that call is paid for: an
	 * MPO request that falls back to the standard template has nowhere to put the copy.
	 *
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant deck design (standard or MPO), may be {@code null} (read as standard)
	 * @return {@code true} when the deck is cloned from an MPO template
	 */
	boolean carriesMpoNarrative(String reportType, DeckTemplate templateVariant);
}
