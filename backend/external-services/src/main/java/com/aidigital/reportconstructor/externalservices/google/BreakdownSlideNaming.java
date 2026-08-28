package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the deterministic object id a duplicated per-tactic breakdown slide gets, so the slide-insertion
 * step and the later chart-linking step agree on the same id without one having to tell the other.
 *
 * <p>The id is derived purely from the {@code (breakdown type, tactic)} pair — the same information both
 * sides already hold — which is what lets the chart step find a slide it never created.
 */
@Component
public class BreakdownSlideNaming {

	/** An EOM tactic copy id, e.g. {@code eom_m1_t3}, capturing the master ordinal and the tactic number. */
	private static final Pattern EOM_TACTIC_COPY = Pattern.compile("^eom_m(\\d{1,2})_t(\\d{1,2})$");

	/**
	 * Builds the deterministic object id for a duplicated breakdown slide, unique per
	 * {@code (breakdown type, tactic)} pair, e.g. {@code bd_dev_3}.
	 *
	 * @param type      the breakdown section
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	public String slideId(BreakdownType type, int tacticNum) {
		return "bd_" + type.code() + "_" + tacticNum;
	}

	/**
	 * Builds the deterministic object id for a duplicated "Thoughts on tactic performance" slide, unique per
	 * tactic, e.g. {@code thoughts_3}. Kept alongside {@link #slideId(BreakdownType, int)} so all duplicated
	 * per-tactic slide ids are minted in one place.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	public String thoughtsSlideId(int tacticNum) {
		return "thoughts_" + tacticNum;
	}

	/**
	 * Builds the deterministic object id for a duplicated main tactic slide, unique per tactic, e.g.
	 * {@code tct_3}. Minted here alongside the breakdown ids because three later steps have to find a slide
	 * they did not create: breakdown insertion anchors its copies after it, the chart step scans it for the
	 * tactic's linked charts, and the trim leaves it alone.
	 *
	 * <p>The prefix is deliberately distinct from the {@code bd_} breakdown prefix, which the breakdown
	 * chart scan filters on.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	public String tacticSlideId(int tacticNum) {
		return "tct_" + tacticNum;
	}

	/**
	 * Builds the deterministic object id for a tactic's copy of one EOM master slide, e.g.
	 * {@code eom_m1_t3}. An EOM tactic gets one copy per master (the deck carries more than one slide
	 * written against the tactic variable), so the id has to carry the master's ordinal as well as the
	 * tactic number — which is why an EOM copy cannot reuse {@link #tacticSlideId(int)}.
	 *
	 * @param masterOrdinal 0-based position of the master among the deck's tactic masters
	 * @param tacticNum     the 1-based tactic number
	 * @return the copy's slide object id
	 */
	public String eomTacticSlideId(int masterOrdinal, int tacticNum) {
		return "eom_m" + masterOrdinal + "_t" + tacticNum;
	}

	/**
	 * Reads the tactic number back out of an EOM tactic copy id, i.e. the inverse of
	 * {@link #eomTacticSlideId(int, int)}. The chart step needs this because the charts it has to replace
	 * sit on slides it never created, and their ids exist nowhere in configuration.
	 *
	 * @param slideObjectId the slide's object id (may be {@code null})
	 * @return the 1-based tactic number, or {@code null} when this is not an EOM tactic copy
	 */
	public Integer eomTacticNumberOf(String slideObjectId) {
		if (slideObjectId == null) {
			return null;
		}
		Matcher matcher = EOM_TACTIC_COPY.matcher(slideObjectId);
		return matcher.matches() ? Integer.valueOf(matcher.group(2)) : null;
	}
}
