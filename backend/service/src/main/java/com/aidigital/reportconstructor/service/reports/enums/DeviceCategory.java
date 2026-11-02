package com.aidigital.reportconstructor.service.reports.enums;

import java.util.List;

/**
 * The four device rows of the "Device breakdown" slide's "PERFORMANCE BY DEVICE" table, in slide order, with
 * the slide tokens each row fills and the sheet labels that map onto it.
 *
 * <p>The sheet's device labels are typed by people, so they are matched loosely: a label is reduced to its
 * lower-case letters and digits and then compared against each device's {@link #labelPrefixes()} by prefix
 * ({@code "Mobile Web"} → {@code "mobileweb"} → {@link #MOBILE}). An exact lookup is what used to drop a
 * filled row on the floor whenever its label was not literally {@code mobile}/{@code ctv}/… — the slide
 * then showed that device as a row of dashes despite the data being in the sheet.
 */
public enum DeviceCategory {

	/** Phones: the {@code {{mobile_*_N}}} row and the {@code {{dev_N_mob}}} share tile. */
	MOBILE("mobile", "mob", true, List.of("mobile", "smartphone", "phone", "cellphone")),

	/**
	 * Connected TV: the {@code {{ctv_*_N}}} row and the {@code {{dev_N_tv}}} share tile. The slide's CTR cell
	 * for this row is a literal dash (non-clickable inventory), so the row carries no CTR token.
	 */
	CTV("ctv", "tv", false, List.of("ctv", "connectedtv", "ott", "smarttv", "tv")),

	/** Desktop and laptop computers: the {@code {{desktop_*_N}}} row and the {@code {{dev_N_desk}}} tile. */
	DESKTOP("desktop", "desk", true, List.of("desktop", "laptop", "computer", "pc")),

	/** Tablets: the {@code {{tablet_*_N}}} row and the {@code {{dev_N_tab}}} share tile. */
	TABLET("tablet", "tab", true, List.of("tablet", "ipad"));

	private final String tokenPrefix;
	private final String shareSuffix;
	private final boolean carriesCtr;
	private final List<String> labelPrefixes;

	DeviceCategory(String tokenPrefix, String shareSuffix, boolean carriesCtr, List<String> labelPrefixes) {
		this.tokenPrefix = tokenPrefix;
		this.shareSuffix = shareSuffix;
		this.carriesCtr = carriesCtr;
		this.labelPrefixes = labelPrefixes;
	}

	/**
	 * Returns the prefix of the device's table-row tokens, e.g. {@code "mobile"} for {@code {{mobile_imps_N}}}.
	 *
	 * @return the row token prefix
	 */
	public String tokenPrefix() {
		return tokenPrefix;
	}

	/**
	 * Returns the suffix of the device's impressions-share tile token, e.g. {@code "mob"} for
	 * {@code {{dev_N_mob}}}. The share tiles were authored under shorter names than the table rows.
	 *
	 * @return the share tile token suffix
	 */
	public String shareSuffix() {
		return shareSuffix;
	}

	/**
	 * Tells whether the device's table row carries a {@code {{<prefix>_ctr_N}}} token.
	 *
	 * @return {@code false} only for Connected TV, whose CTR cell is a literal dash on the slide
	 */
	public boolean carriesCtr() {
		return carriesCtr;
	}

	/**
	 * Returns the normalized (lower-case, letters and digits only) label prefixes that identify this device.
	 *
	 * @return the label prefixes, most specific first
	 */
	public List<String> labelPrefixes() {
		return labelPrefixes;
	}

	/**
	 * Tells whether an already-normalized sheet label names this device.
	 *
	 * @param normalizedLabel the label reduced to lower-case letters and digits
	 * @return {@code true} when the label starts with one of the device's prefixes
	 */
	public boolean matches(String normalizedLabel) {
		if (normalizedLabel == null || normalizedLabel.isEmpty()) {
			return false;
		}
		for (String prefix : labelPrefixes) {
			if (normalizedLabel.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}
}
