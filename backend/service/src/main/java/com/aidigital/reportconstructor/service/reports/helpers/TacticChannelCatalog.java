package com.aidigital.reportconstructor.service.reports.helpers;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which BigQuery channel(s) a Media-Plan tactic is expected to deliver under, for line-item matching.
 */
@Component
public class TacticChannelCatalog {

	/**
	 * Tactic (lowercase) &rarr; expected BQ Channel value(s). The first channel
	 * with exactly one line item ID wins. Multiple entries mean a tactic can
	 * appear under any of those channels. Kept distinct from the collector's
	 * channel filter, whose values differ — e.g. geofencing.
	 */
	private static final Map<String, List<String>> TACTIC_CHANNEL_MAP = new LinkedHashMap<>();

	static {
		Map<String, List<String>> m = TACTIC_CHANNEL_MAP;
		// CTV
		m.put("programmatic ctv", List.of("CTV"));
		m.put("streaming tv", List.of("CTV"));
		m.put("ctv precision reach", List.of("CTV"));
		m.put("ctv select", List.of("CTV"));
		m.put("network select bundle ctv", List.of("CTV"));
		m.put("network exclusive", List.of("CTV"));
		m.put("live news", List.of("CTV"));
		m.put("live tv", List.of("CTV"));
		m.put("local ctv", List.of("CTV"));
		m.put("zip code targeted ctv", List.of("CTV"));
		m.put("amazon fire tv", List.of("CTV"));
		m.put("google tv", List.of("CTV"));
		m.put("netflix (up to 10 sec creative)", List.of("CTV"));
		m.put("netflix (up to 15 sec creative)", List.of("CTV"));
		m.put("netflix (up to 30 sec creative)", List.of("CTV"));
		// OTT
		m.put("ott precision reach", List.of("OTT"));
		// CTV/OTT
		m.put("blended set ctv/ott", List.of("CTV/OTT"));
		m.put("blended set ctv ott", List.of("CTV/OTT"));
		// CTV Live Sports
		m.put("100% live sports package (100% live and in-game inventory)", List.of("CTV Live Sports"));
		m.put("any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)", List.of(
				"CTV" +
				" Live Sports"));
		m.put("any live sports package (up to 80% live sports inventory / up to 20% shoulder inventory)", List.of(
				"CTV Live Sports"));
		m.put("any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)", List.of(
				"CTV Live Sports"));
		m.put("college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				List.of("CTV Live Sports"));
		m.put("college football live sport package (up to 50% live sports inventory / up to 50% ancillary inventory)",
				List.of("CTV Live Sports"));
		// YouTube (all formats)
		m.put("youtube skippable in-stream", List.of("YouTube"));
		m.put("youtube skippable in-stream (cpm)", List.of("YouTube"));
		m.put("youtube non-skippable in-stream", List.of("YouTube"));
		m.put("youtube bumper ads", List.of("YouTube"));
		m.put("youtube in-feed (ex. discovery)", List.of("YouTube"));
		m.put("youtube in-feed", List.of("YouTube"));
		m.put("youtube demand gen", List.of("YouTube"));
		m.put("youtube shorts", List.of("YouTube"));
		m.put("youtube ctv skippable in-stream", List.of("YouTube"));
		m.put("youtube ctv non-skippable in-stream", List.of("YouTube"));
		m.put("youtube tv (up to 15 sec)", List.of("YouTube"));
		m.put("youtube tv (up to 30 sec)", List.of("YouTube"));
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)", List.of("YouTube"));
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", List.of("YouTube"));
		// Display
		m.put("programmatic display", List.of("Display"));
		m.put("geofencing (display)", List.of("Display"));
		m.put("geofencing display", List.of("Display"));
		m.put("programmatic mobile display", List.of("In-App Display"));
		m.put("native display", List.of("Native"));
		m.put("rich media (html 5)", List.of("Rich Media"));
		m.put("rich media html 5", List.of("Rich Media"));
		m.put("gdn specific", List.of("Display"));
		// Video
		m.put("programmatic video", List.of("Video"));
		m.put("native video", List.of("Native Video"));
		// Audio
		m.put("programmatic audio", List.of("Audio"));
		m.put("blended programmatic audio", List.of("Audio"));
		m.put("amazon podcast ads", List.of("Audio"));
		// Social
		m.put("meta (cpm)", List.of("Meta"));
		m.put("meta (cpc)", List.of("Meta"));
		m.put("meta lead forms", List.of("Meta"));
		m.put("meta boosted posts", List.of("Meta"));
		m.put("facebook specific", List.of("Meta"));
		m.put("instagram specific", List.of("Meta"));
		m.put("tiktok (cpm)", List.of("TikTok"));
		m.put("tiktok (cpc)", List.of("TikTok"));
		m.put("tiktok spark ads (cpm)", List.of("TikTok"));
		m.put("tiktok spark ads (cpc)", List.of("TikTok"));
		m.put("tiktok search ads", List.of("TikTok"));
		m.put("linkedin (cpm)", List.of("LinkedIn"));
		m.put("linkedin (cpc)", List.of("LinkedIn"));
		m.put("pinterest (cpm)", List.of("Pinterest"));
		m.put("pinterest (cpc)", List.of("Pinterest"));
		m.put("reddit (cpm)", List.of("Reddit"));
		m.put("reddit (cpc)", List.of("Reddit"));
		m.put("snapchat (cpm)", List.of("Snapchat"));
		m.put("twitter", List.of("Twitter"));
		// Search / SEM
		m.put("google sem", List.of("Google Search"));
		m.put("demand gen", List.of("Google Search"));
		m.put("performance max", List.of("Performance Max"));
		m.put("bing", List.of("Bing Search"));
		m.put("app (google uac)", List.of("Google App"));
		m.put("google uac", List.of("Google App"));
		m.put("apple search ads", List.of("Apple Search"));
		// Amazon
		m.put("amazon display (amazon & publisher network)", List.of("Amazon Display"));
		m.put("amazon display", List.of("Amazon Display"));
		m.put("amazon video (amazon & publisher network)", List.of("Amazon Video"));
		m.put("amazon video", List.of("Amazon Video"));
		m.put("amazon audio (amazon & publisher network)", List.of("Audio"));
		m.put("amazon sponsored ads", List.of("Amazon Search"));
		m.put("twitch", List.of("Amazon Video Twitch"));
		// Other
		m.put("dooh", List.of("DOOH"));
	}

	/**
	 * Looks up the expected BQ channel(s) for a Media-Plan tactic name.
	 *
	 * @param tacticName the tactic name in original casing
	 * @return the expected channel list, or {@code null} when the tactic is unknown
	 */
	public List<String> channelsFor(String tacticName) {
		return TACTIC_CHANNEL_MAP.get(tacticName.trim().toLowerCase(Locale.ROOT));
	}
}
