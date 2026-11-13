package com.aidigital.reportconstructor.service.reports.claude;

import com.aidigital.reportconstructor.service.reports.enums.NarrativeCopyLimits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaudeResponseNormalizerTest {

	private final ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void normalizeProposal_truncatesAtSentenceWhenPastHalfLimit() {
		String longText = "A. " + "x".repeat(500);
		String out = normalizer.normalizeProposal(longText, 400);
		assertEquals("A.", out);
	}

	@Test
	void normalizeC_shortTextUnchanged() {
		assertEquals("short overview.", normalizer.normalizeC("short overview.", 400));
	}

	@Test
	void limitFOpportunity_capsAt180Chars() {
		String out = normalizer.limitFOpportunity("word " + "x".repeat(300) + ".");
		assertThat(out.length()).isLessThanOrEqualTo(180);
	}

	@Test
	void limitFFact_capsAt140Chars() {
		String out = normalizer.limitFFact("word " + "x".repeat(300) + ".");
		assertThat(out.length()).isLessThanOrEqualTo(140);
	}

	@Test
	void limitFStorytelling_capsAt320Chars() {
		String out = normalizer.limitFStorytelling("word " + "x".repeat(500) + ".");
		assertThat(out.length()).isLessThanOrEqualTo(320);
	}

	@Test
	void limitFFact_shortTextUnchanged() {
		assertEquals("Actual frequency was 3.16, aligned with our plan.",
				normalizer.limitFFact("Actual frequency was 3.16, aligned with our plan."));
	}

	@Test
	void normalizeThoughts_splitsPipeIntoFour() {
		List<String> slots = normalizer.normalizeThoughts("one | two | three | four");
		assertEquals("one", slots.get(0));
		assertEquals("two", slots.get(1));
		assertEquals("three", slots.get(2));
		assertEquals("four", slots.get(3));
	}

	@Test
	void limitStrategicPoint_max22Chars() {
		assertThat(normalizer.limitStrategicPoint("abcdefghijklmnopqrstuvwxyz")).hasSize(22);
	}

	@Test
	void limitRecommendationTitle_max30CharsAtWordBoundary() {
		String out = normalizer.limitRecommendationTitle("Scale Connected TV In Evening Dayparts Significantly");
		assertThat(out.length()).isLessThanOrEqualTo(30);
		assertThat(out).doesNotEndWith(" ");
		assertThat(out).startsWith("Scale Connected TV");
	}

	@Test
	void limitRecommendationTitle_nullBecomesEmpty() {
		assertThat(normalizer.limitRecommendationTitle(null)).isEmpty();
	}

	@Test
	void limitRecommendationText_max130CharsEndsOnSentence() {
		String longText = "x".repeat(200);
		String out = normalizer.limitRecommendationText(longText);
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(130);
	}

	@Test
	void limitRecommendationText_shortTextUnchanged() {
		assertEquals("Reallocate spend to CTV.", normalizer.limitRecommendationText("Reallocate spend to CTV."));
	}

	@Test
	void limitAudienceSegments_max80AtComma() {
		String longSeg =
				"one, two, three, four, five, six, seven, eight, nine, ten, eleven, twelve, thirteen, fourteen, " +
						"fifteen, sixteen";
		assertThat(longSeg.length()).isGreaterThan(80);
		String out = normalizer.limitAudienceSegments(longSeg);
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(80);
		assertThat(out).doesNotContain("thirteen");
		assertThat(out).endsWith("twelve");
	}

	@Test
	void limitAudienceSegments_honoursTheWiderEndOfMonthBudgetTest() {
		// Given: a segments line longer than the EOC budget but inside the EOM one
		String seg = "Eco-conscious household decision-makers, parents 25+, sustainability-minded shoppers";
		assertThat(seg.length()).isGreaterThan(80);
		assertThat(seg.length()).isLessThan(150);

		// When-Then: the EOM budget keeps it whole, the EOC one trims it back to a comma
		assertThat(normalizer.limitAudienceSegments(seg, 150)).isEqualTo(seg);
		assertThat(normalizer.limitAudienceSegments(seg)).isEqualTo(
				"Eco-conscious household decision-makers, parents 25+");
	}

	@Test
	void limitNorthStar_capsAtEightyCharactersAndUpperCasesTest() {
		// Given: a north star written past the slot's width, in sentence case, ending on a period
		String northStar = "Deep penetration of the high-HHI luxury-travel audience across Southern California "
				+ "and the wider West Coast.";
		assertThat(northStar.length()).isGreaterThan(80);

		// When:
		String out = normalizer.limitNorthStar(northStar);

		// Then: it is cut on a word boundary, upper-cased, and left without a trailing period
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(80);
		assertThat(out).isEqualTo("DEEP PENETRATION OF THE HIGH-HHI LUXURY-TRAVEL AUDIENCE ACROSS SOUTHERN");
	}

	@Test
	void limitNorthStar_upperCasesAShortHeadlineWithoutCuttingItTest() {
		// Given: a headline already inside the budget
		String northStar = "Deep penetration of the high-HHI luxury-travel audience.";

		// When-Then: only the case and the trailing period change
		assertThat(normalizer.limitNorthStar(northStar))
				.isEqualTo("DEEP PENETRATION OF THE HIGH-HHI LUXURY-TRAVEL AUDIENCE");
		assertThat(normalizer.limitNorthStar(null)).isNull();
	}

	@Test
	void normalizeThoughts_splitsIntoExactlyFour() {
		List<String> slots = normalizer.normalizeThoughts("a | b | c | d | e");
		assertThat(slots).hasSize(4);
		assertThat(slots.get(3)).isEqualTo("d");
	}

	@Test
	void shouldSplitThoughtsOnBarePipeWithoutSpacesTest() {
		// Given: a reply that joined the paragraphs without the requested spaces around the pipe
		String reply = "one|two | three|  four";

		// When: the field is normalized
		List<String> slots = normalizer.normalizeThoughts(reply);

		// Then: each paragraph lands in its own slot instead of collapsing into the first
		assertThat(slots).containsExactly("one", "two", "three", "four");
	}

	@Test
	void shouldReadThoughtsFromArrayReplyTest() throws Exception {
		// Given: a reply that returned the paragraphs as a JSON array instead of a pipe-joined string
		var node = json.readTree("[\"one\", \"two\", \"three\", \"four\", \"five\"]");

		// When: the node is normalized
		List<String> slots = normalizer.normalizeThoughts(node);

		// Then: the first four paragraphs fill the slots and no raw JSON leaks into slot one
		assertThat(slots).containsExactly("one", "two", "three", "four");
	}

	@Test
	void shouldReturnEmptySlotsWhenThoughtsNodeIsMissingTest() {
		// Given: the reply carried no thoughts_on_performance field

		// When: the absent node is normalized
		List<String> slots = normalizer.normalizeThoughts((JsonNode) null);

		// Then: four empty slots come back rather than a failure
		assertThat(slots).containsExactly(null, null, null, null);
	}

	@Test
	void limitGeoSummary_max40() {
		String out = normalizer.limitGeoSummary("New York, Los Angeles, Chicago, Houston, Phoenix");
		assertThat(out).hasSizeLessThanOrEqualTo(40);
	}

	@Test
	void shouldStripQuotesAndTrailingPeriodFromPrimaryKpisTest() {
		// Given: the model wrapped the KPI line in quotes and added a trailing period
		String out = normalizer.limitPrimaryKpis("\"Imps, CTR, VCR, R&F\".");

		// Then: the quotes and trailing period are removed, leaving a clean single line
		assertThat(out).isEqualTo("Imps, CTR, VCR, R&F");
	}

	@Test
	void shouldReturnNullForBlankPrimaryKpisTest() {
		// When-Then:
		assertThat(normalizer.limitPrimaryKpis("   ")).isNull();
	}

	@Test
	void limitResultsOverview_uses200Budget() {
		String longText = "x".repeat(500);
		String out = normalizer.limitResultsOverview(longText);
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(200);
	}

	@Test
	void limitTacticOverview_uses210Budget() {
		String out = normalizer.limitTacticOverview("word ".repeat(80));
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(210);
	}

	@Test
	void shouldFallBackToLastWordBoundaryWhenNoPunctuationFoundTest() {
		// Given: words separated by spaces, no '.'/',' anywhere, longer than the 210-char budget
		String longText = "alpha ".repeat(60);

		// When:
		String out = normalizer.normalizeC(longText, 210);

		// Then: cut lands on a space boundary (never mid-word) and is finished with a period
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(210);
		assertThat(out).doesNotEndWith("alph.");
		assertThat(out).endsWith("alpha.");
		String body = out.substring(0, out.length() - 1);
		assertThat(longText.substring(0, body.length() + 1)).startsWith(body + " ");
	}

	@Test
	void shouldFallBackToLastWordBoundaryForStrategicOverviewTest() {
		// Given: a strategic overview with no sentence-ending punctuation, longer than the 240-char budget
		String longOverview = "tactic ".repeat(40);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: cut lands on a space boundary (never mid-word) and is finished with a period
		assertThat(out.length()).isLessThanOrEqualTo(240);
		assertThat(out).doesNotEndWith("tacti.");
		assertThat(out).endsWith("tactic.");
		String body = out.substring(0, out.length() - 1);
		assertThat(longOverview.substring(0, body.length() + 1)).startsWith(body + " ");
	}

	@Test
	void shouldPreferSentenceEndingPeriodOverALaterCommaTest() {
		// Given: a period sits just past the 75% threshold, and a comma sits further along but still
		// inside the cut window — picking the later (comma) would end on an unfinished clause
		String longText = "a".repeat(160) + "." + "b".repeat(20) + "," + "c".repeat(50);

		// When:
		String out = normalizer.normalizeC(longText, 210);

		// Then: cuts at the period, not the later comma, so the result is a finished sentence
		assertThat(out).isNotNull();
		assertThat(out).endsWith(".");
		assertThat(out).doesNotContain(",");
	}

	@Test
	void shouldPreferSentenceEndingPeriodOverALaterCommaForStrategicOverviewTest() {
		// Given: a period sits just past position 180, and a comma sits further along but still inside
		// the 240-char cut window
		String longOverview = "a".repeat(190) + "." + "b".repeat(20) + "," + "c".repeat(50);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: cuts at the period, not the later comma
		assertThat(out).endsWith(".");
		assertThat(out).doesNotContain(",");
	}

	@Test
	void shouldNotTreatADecimalPointAsASentenceEndingPeriodTest() {
		// Given: the only "." in the cut window is a decimal point inside a number (e.g. "94.72"),
		// not a real sentence end, followed by plain text with no further punctuation
		String longOverview = "Programmatic delivered a 94.72 percent rate " + "across the board ".repeat(15);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: the decimal point is not mistaken for a sentence boundary
		assertThat(out).doesNotEndWith("94.");
		assertThat(out.length()).isLessThanOrEqualTo(240);
	}

	@Test
	void shouldStripDanglingTrailingCommaWhenFallingBackToWordBoundaryTest() {
		// Given: no sentence-ending period anywhere, but the word-boundary cut lands right after a comma
		// in a list (e.g. "... buyers, shoppers,")
		String longOverview = "Layered intent segments " + "buyers, shoppers, decision makers, ".repeat(8);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: the dangling comma is removed instead of left as the visible ending
		assertThat(out).doesNotEndWith(",");
		assertThat(out.length()).isLessThanOrEqualTo(240);
	}

	@Test
	void shouldDropTrailingPrepositionAndAddPeriodWhenFinishingSentenceTest() {
		// Given: a fragment left by a word-boundary cut that ends on a dangling preposition
		String fragment = "validating video as the primary engagement driver for";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then: the dangling word is dropped and the result reads as a finished sentence
		assertEquals("validating video as the primary engagement driver.", out);
	}

	@Test
	void shouldDropTrailingVerbAndAddPeriodWhenFinishingSentenceTest() {
		// Given: a fragment that ends on a dangling auxiliary verb
		String fragment = "matched audience intent and the segment strategy is";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then:
		assertEquals("matched audience intent and the segment strategy.", out);
	}

	@Test
	void shouldAddPeriodWhenTailIsAContentWordTest() {
		// Given: a fragment whose last word carries meaning, just missing terminal punctuation
		String fragment = "concentrated delivery within the trade zones";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then: nothing is dropped, only a period is appended
		assertEquals("concentrated delivery within the trade zones.", out);
	}

	@Test
	void shouldLeaveAlreadyCompleteSentenceUnchangedWhenFinishingTest() {
		// Given: a fragment that already ends on sentence-ending punctuation
		String fragment = "the campaign delivered qualified local reach efficiently.";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then:
		assertEquals("the campaign delivered qualified local reach efficiently.", out);
	}

	@Test
	void shouldEndOnCompleteSentenceAfterWordBoundaryFallbackInNormalizeCTest() {
		// Given: prose longer than the 210-char budget, no sentence-ending period anywhere, engineered so
		// the word-boundary cut lands on a dangling connector word
		String longText = ("the team kept spending across many channels and "
				+ "validating the approach with ").repeat(6);

		// When:
		String out = normalizer.normalizeC(longText, 210);

		// Then: the result fits and reads as a finished thought rather than ending mid-clause
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(211);
		assertThat(out).endsWith(".");
		assertThat(out).doesNotEndWith(" with.");
		assertThat(out).doesNotEndWith(" and.");
		assertThat(out).doesNotEndWith(" the.");
	}

	@Test
	void shouldCapTheStrategicOverviewAtTheMpoBudgetForTheCompactProfileTest() {
		// Given: a normalizer bound to the MPO deck, a period just past position 75, and a comma after it
		// still inside the 100-character cut window
		ClaudeResponseNormalizer compact = new ClaudeResponseNormalizer().withCopyLimits(NarrativeCopyLimits.COMPACT);
		String longOverview = "a".repeat(95) + "." + "b".repeat(10) + "," + "c".repeat(50);

		// When:
		String out = compact.limitStrategicOverview(longOverview);
		String standard = normalizer.limitStrategicOverview(longOverview);

		// Then: the MPO cap cuts at the period, while the standard template's wider box keeps the whole text
		assertThat(out).isEqualTo("a".repeat(95) + ".");
		assertThat(standard).isEqualTo(longOverview);
		assertThat(compact.getCopyLimits()).isEqualTo(NarrativeCopyLimits.COMPACT);
		assertThat(normalizer.getCopyLimits()).isEqualTo(NarrativeCopyLimits.STANDARD);
	}

	@Test
	void shouldFindLastSentencePeriodSkippingDecimalPointsTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then: a closing period counts, a decimal point does not
		assertThat(target.lastSentencePeriod("Done.")).isEqualTo(4);
		assertThat(target.lastSentencePeriod("End. CTR 1.5")).isEqualTo(3);
		assertThat(target.lastSentencePeriod("CTR rose 1.5 points")).isEqualTo(-1);
	}

	@Test
	void shouldReturnNullOrEmptyInputUnchangedWhenFinishingSentenceTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.finishSentence(null)).isNull();
		assertThat(target.finishSentence("")).isEmpty();
	}

	@Test
	void shouldKeepOriginalFragmentWhenEveryWordIsADanglingConnectorTest() {
		// Given: a fragment made only of function words, which trimming would erase
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When:
		String out = target.finishSentence("the and");

		// Then: the trimmed original is returned rather than an empty string
		assertThat(out).isEqualTo("the and");
	}

	@Test
	void shouldNotTreatANumericTailAsADanglingWordTest() {
		// Given: a fragment ending on a number, whose letter-only form is empty
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When:
		String out = target.finishSentence("revenue grew 123");

		// Then: the number stays and a period is appended
		assertThat(out).isEqualTo("revenue grew 123.");
	}

	@Test
	void shouldStopAtExistingPunctuationAfterDroppingDanglingWordTest() {
		// Given: a dangling connector follows an exclamation
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When:
		String out = target.finishSentence("Great result! and");

		// Then: no extra period is appended after the exclamation mark
		assertThat(out).isEqualTo("Great result!");
	}

	@Test
	void shouldRecognizeEverySentenceEndingPunctuationMarkTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.endsWithSentencePunctuation("Done.")).isTrue();
		assertThat(target.endsWithSentencePunctuation("Wow!")).isTrue();
		assertThat(target.endsWithSentencePunctuation("Why?")).isTrue();
		assertThat(target.endsWithSentencePunctuation("Hmm")).isFalse();
	}

	@Test
	void shouldReturnNullProposalForNullOrBlankTextTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.normalizeProposal(null, 100)).isNull();
		assertThat(target.normalizeProposal("   ", 100)).isNull();
	}

	@Test
	void shouldCutProposalAtWordBoundaryWhenNoPeriodPastHalfLimitTest() {
		// Given: a long reply without any period
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();
		String reply = "word ".repeat(100);

		// When:
		String out = target.normalizeProposal(reply, 100);

		// Then: it is cut at the last space inside the budget and closed with a period
		assertThat(out).isEqualTo("word ".repeat(20).trim() + ".");
	}

	@Test
	void shouldCutProposalAtSentenceEndPastHalfLimitTest() {
		// Given: a period at position 300 of a reply longer than the 400-character budget
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();
		String reply = "a".repeat(300) + ". " + "b".repeat(200);

		// When:
		String out = target.normalizeProposal(reply, 400);

		// Then: the reply ends on that sentence
		assertThat(out).isEqualTo("a".repeat(300) + ".");
	}

	@Test
	void shouldHardCutProposalWithoutSpacesTest() {
		// Given: an unbroken blob longer than the budget
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When:
		String out = target.normalizeProposal("x".repeat(500), 400);

		// Then: the blob is cut at the budget and gets no period
		assertThat(out).isEqualTo("x".repeat(400));
	}

	@Test
	void shouldReturnNullWhenAZeroBudgetLeavesNothingTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then: nothing fits a zero budget, so both normalizers report no copy
		assertThat(target.normalizeProposal("abc", 0)).isNull();
		assertThat(target.normalizeC("abc", 0)).isNull();
	}

	@Test
	void shouldFillOnlyAvailableSlotsFromShortArrayReplyTest() throws Exception {
		// Given: an array reply with one paragraph and one blank entry
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();
		JsonNode node = new ObjectMapper().readTree("[\"one\", \"  \"]");

		// When:
		List<String> slots = target.normalizeThoughts(node);

		// Then:
		assertThat(slots).containsExactly("one", null, null, null);
	}

	@Test
	void shouldSplitShortAndGappedPipeStringsIntoFourSlotsTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When:
		List<String> blank = target.normalizeThoughts("   ");
		List<String> shortList = target.normalizeThoughts("a | b");
		List<String> gapped = target.normalizeThoughts("a || c");

		// Then:
		assertThat(blank).containsExactly(null, null, null, null);
		assertThat(shortList).containsExactly("a", "b", null, null);
		assertThat(gapped).containsExactly("a", null, "c", null);
	}

	@Test
	void shouldReadNodeTextOrSerializedFormTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();
		JsonNodeFactory nodes = JsonNodeFactory.instance;

		// When-Then:
		assertThat(target.textOrNull(null)).isNull();
		assertThat(target.textOrNull(nodes.nullNode())).isNull();
		assertThat(target.textOrNull(nodes.textNode(""))).isNull();
		assertThat(target.textOrNull(nodes.textNode("hi"))).isEqualTo("hi");
		assertThat(target.textOrNull(nodes.numberNode(5))).isEqualTo("5");
	}

	@Test
	void shouldReportNotBlankOnlyForTextWithContentTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.notBlank(null)).isFalse();
		assertThat(target.notBlank("  ")).isFalse();
		assertThat(target.notBlank("x")).isTrue();
	}

	@Test
	void shouldLimitAudienceSegmentsAtLastCommaOrHardCutTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();
		String withComma = "Sports fans, " + "y".repeat(80);

		// When-Then:
		assertThat(target.limitAudienceSegments(null)).isNull();
		assertThat(target.limitAudienceSegments(" Not Specified ")).isNull();
		assertThat(target.limitAudienceSegments("   ")).isNull();
		assertThat(target.limitAudienceSegments("x".repeat(100))).isEqualTo("x".repeat(80));
		assertThat(target.limitAudienceSegments(withComma)).isEqualTo("Sports fans");
		assertThat(target.limitAudienceSegments(withComma, 200)).isEqualTo(withComma);
	}

	@Test
	void shouldLimitNorthStarAndReturnNullWhenNothingRemainsTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.limitNorthStar(null)).isNull();
		assertThat(target.limitNorthStar(" . ")).isNull();
		assertThat(target.limitNorthStar("x".repeat(100))).isEqualTo("X".repeat(80));
		assertThat(target.limitNorthStar("grow " + "reach ".repeat(20))).isEqualTo(
				("GROW " + "REACH ".repeat(12)).trim());
	}

	@Test
	void shouldLimitWhatWeDidHeadingAtWordBoundaryWithoutPeriodTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.limitWhatWeDidHeading(null)).isNull();
		assertThat(target.limitWhatWeDidHeading(" . ")).isNull();
		assertThat(target.limitWhatWeDidHeading("Shifted budget.")).isEqualTo("Shifted budget");
		assertThat(target.limitWhatWeDidHeading("word ".repeat(20))).isEqualTo("word ".repeat(12).trim());
		assertThat(target.limitWhatWeDidHeading("y".repeat(70))).isEqualTo("y".repeat(60));
	}

	@Test
	void shouldLimitFocusItemAtWordBoundaryWithoutPeriodTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.limitFocusItem(null)).isNull();
		assertThat(target.limitFocusItem(" . ")).isNull();
		assertThat(target.limitFocusItem("Hold top markets.")).isEqualTo("Hold top markets");
		assertThat(target.limitFocusItem("word ".repeat(20))).isEqualTo("word ".repeat(14).trim());
		assertThat(target.limitFocusItem("z".repeat(80))).isEqualTo("z".repeat(70));
	}

	@Test
	void shouldStripOnlyASingleTrailingPeriodTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then: an ellipsis is left alone
		assertThat(target.stripTrailingPeriod("Done.")).isEqualTo("Done");
		assertThat(target.stripTrailingPeriod("Wait...")).isEqualTo("Wait...");
		assertThat(target.stripTrailingPeriod("Done")).isEqualTo("Done");
	}

	@Test
	void shouldCutStrategicPointAtPeriodOrWordBoundaryTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then: a period past position 11 wins, otherwise the dangling comma is dropped at the last space
		assertThat(target.limitStrategicPoint(null)).isEmpty();
		assertThat(target.limitStrategicPoint("Grow reach now. Then scale up")).isEqualTo("Grow reach now.");
		assertThat(target.limitStrategicPoint("Drive awareness, convert more")).isEqualTo("Drive awareness");
	}

	@Test
	void shouldHardCutStrategicOverviewWithoutSpacesTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then: an unbroken blob is cut at the 240-character budget without a period
		assertThat(target.limitStrategicOverview(null)).isEmpty();
		assertThat(target.limitStrategicOverview("a".repeat(300))).isEqualTo("a".repeat(240));
	}

	@Test
	void shouldCutRecommendationTitleAtPeriodOrHardCutTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.limitRecommendationTitle("Scale CTV in prime time. Then add more"))
				.isEqualTo("Scale CTV in prime time.");
		assertThat(target.limitRecommendationTitle("x".repeat(40))).isEqualTo("x".repeat(30));
	}

	@Test
	void shouldLimitGeoSummaryAtPeriodOrHardCutTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.limitGeoSummary(null)).isNull();
		assertThat(target.limitGeoSummary("   ")).isNull();
		assertThat(target.limitGeoSummary("Texas led every market. Oklahoma lagged far behind"))
				.isEqualTo("Texas led every market.");
		assertThat(target.limitGeoSummary("g".repeat(50))).isEqualTo("g".repeat(40));
		assertThat(target.limitGeoSummary(", " + "x".repeat(50))).isNull();
		assertThat(target.limitGeoSummary("Texas, Oklahoma, Kansas, Missouri, Arkansas"))
				.isEqualTo("Texas, Oklahoma, Kansas, Missouri");
	}

	@Test
	void shouldCapPrimaryKpisAndReturnNullWhenOnlyPunctuationRemainsTest() {
		// Given:
		ClaudeResponseNormalizer target = new ClaudeResponseNormalizer();

		// When-Then:
		assertThat(target.limitPrimaryKpis(null)).isNull();
		assertThat(target.limitPrimaryKpis("'..'")).isNull();
		assertThat(target.limitPrimaryKpis("a".repeat(59) + ", more")).isEqualTo("a".repeat(59));
	}
}
