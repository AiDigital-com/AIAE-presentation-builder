// Token markup spec for the EOC WIP deck's paid slides. Element ids are suffixes of h6ba452c992440554_1_.
// Modes: all = replace the element's whole text; para = replace paragraph i (others kept);
// prefix = keep `prefix` of paragraph 0 and replace the rest; alt = set the table's alt-text title.
const all = (id, tok) => ({ id, mode: 'all', tok });
const para = (id, i, tok) => ({ id, mode: 'para', i, tok });
const prefix = (id, prefix, tok) => ({ id, mode: 'prefix', prefix, tok });
const alt = (id, title) => ({ id, mode: 'alt', title });
const pair = (titleId, textId, base) => [all(titleId, `{{${base}_title}}`), all(textId, `{{${base}}}`)];
const titled = (id, base) => [para(id, 0, `{{${base}_title}}`), para(id, 1, `{{${base}}}`)];
const top = (id, base) => [para(id, 0, `{{${base}_name}}`), para(id, 1, `{{${base}_share}} OF CONVERSIONS`)];
const metric = (id, tok) => para(id, 1, tok);

module.exports = {
  // ---------------- Paid Search ----------------
  ps_divider: [all(6, '{{ps_conv}}'), all(10, '{{ps_cpa}}')],
  ps_overview: [
    all(18, '{{ps_verdict}}'),
    all(21, '{{ps_spend}}'), all(24, '{{ps_conv}}'), all(27, '{{ps_cpa}}'),
    all(30, '{{ps_imps}}'), all(33, '{{ps_clicks}}'), all(36, '{{ps_ctr}}'),
    all(37, '{{ps_donut_1}}'), all(39, '{{ps_donut_2}}'), all(83, '{{ps_donut_3}}'),
    all(50, '{{ps_clicks_plan}}'), all(53, '{{ps_clicks}}'),
    all(58, '{{ps_male}}'), all(61, '{{ps_female}}'),
    all(65, '{{ps_ad_1_conv}}'), all(68, '{{ps_ad_1_clicks}}'), all(74, '{{ps_ad_1_cpa}}'),
    all(79, 'Ad · {{ps_ad_1_url}}'), all(80, '{{ps_ad_1_headline}}'), all(81, '{{ps_ad_1_desc}}'),
  ],
  ps_campaign: [alt(124, 'ps_camp_table')],
  ps_group: [
    all(138, 'Performance by ad group within each campaign, ranked by conversions.'),
    alt(164, 'ps_grp_table'),
    all(166, '{{ps_camp_1_name}}'), all(169, '{{ps_camp_2_name}}'), all(172, '{{ps_camp_3_name}}'),
    all(185, '{{ps_grp_callout_1}}'), all(186, '{{ps_grp_callout_2}}'), all(187, '{{ps_grp_callout_3}}'),
  ],
  ps_keywords: [
    all(213, '{{ps_kw_takeaway_1}}'), all(215, '{{ps_kw_takeaway_2}}'), all(218, '{{ps_kw_takeaway_3}}'),
    alt(216, 'ps_kw_table'),
    all(232, 'Top keywords by conversions across all search campaigns.'),
  ],
  ps_pmax: [
    all(243, '{{ps_pmax_conv}}'), all(245, '{{ps_pmax_best_cpc}}'), all(247, '{{ps_pmax_best_cpa}}'),
    ...titled(285, 'ps_pmax_take_1'), ...titled(286, 'ps_pmax_take_2'),
    ...titled(287, 'ps_pmax_take_3'), ...titled(288, 'ps_pmax_take_4'),
    ...top(291, 'ps_pmax_top'),
    alt(293, 'ps_pmax_table'),
  ],
  ps_audience: [
    all(315, '{{ps_conv}}'), all(318, '{{ps_cpa}}'), all(321, '{{ps_cpc}}'),
    para(324, 0, '{{ps_age_top_name}}'), para(324, 1, '{{ps_age_top_share}} OF CONVERSIONS'),
    all(330, '{{ps_age_1_share}}'), all(332, '{{ps_age_2_share}}'), all(334, '{{ps_age_3_share}}'),
    all(336, '{{ps_age_4_share}}'), all(338, '{{ps_age_5_share}}'), all(340, '{{ps_age_6_share}}'),
    all(346, '{{ps_male_conv}} conversions'), all(347, '{{ps_female_conv}} conversions'),
    prefix(349, 'Recommendation: ', '{{ps_aud_reco}}'),
    para(354, 0, '{{ps_aud_takeaway_1}}'), para(354, 1, '{{ps_aud_takeaway_2}}'), para(354, 2, '{{ps_aud_takeaway_3}}'),
    all(409, '{{ps_male_conv_share}}'), all(410, '{{ps_female_conv_share}}'),
  ],
  ps_ads: [
    all(416, 'Top responsive search ads by conversions, with their copy and efficiency.'),
    ...[[1, 418], [2, 432], [3, 446]].flatMap(([n, b]) => [
      prefix(b, `#${n}  `, `{{ps_ad_${n}_badge}}`),
      all(b + 1, `{{ps_ad_${n}_name}}`),
      all(b + 2, `{{ps_ad_${n}_campaign}} · {{ps_ad_${n}_group}}`),
      all(b + 4, `Sponsored · {{ps_ad_${n}_url}}`),
      all(b + 5, `{{ps_ad_${n}_headline}}`),
      all(b + 6, `{{ps_ad_${n}_desc}}`),
      metric(b + 9, `{{ps_ad_${n}_clicks}}`), metric(b + 10, `{{ps_ad_${n}_ctr}}`),
      metric(b + 11, `{{ps_ad_${n}_conv}}`), metric(b + 12, `{{ps_ad_${n}_cpa}}`),
    ]),
  ],
  ps_highlights: [
    ...pair(480, 465, 'ps_highlight_1'), ...pair(481, 477, 'ps_highlight_2'),
    ...pair(482, 469, 'ps_highlight_3'), ...pair(483, 473, 'ps_highlight_4'),
  ],
  ps_recos: [
    ...pair(490, 491, 'ps_reco_1'), ...pair(496, 497, 'ps_reco_2'),
    ...pair(501, 502, 'ps_reco_3'), ...pair(506, 507, 'ps_reco_4'),
  ],
  // ---------------- Paid Social ----------------
  pso_divider: [all(518, '{{pso_conv}}'), all(522, '{{pso_cpa}}')],
  pso_overview: [
    all(530, '{{pso_verdict}}'),
    all(533, '{{pso_spend}}'), all(537, '{{pso_conv}}'), all(541, '{{pso_cpa}}'),
    all(545, '{{pso_imps}}'), all(549, '{{pso_clicks}}'), all(553, '{{pso_ctr}}'),
    all(556, '{{pso_donut_1}}'), all(558, '{{pso_donut_2}}'),
    all(569, '{{pso_clicks_plan}}'), all(572, '{{pso_clicks}}'),
    all(577, '{{pso_male}}'), all(580, '{{pso_female}}'),
    all(587, '{{pso_ad_conv}}'), all(590, '{{pso_ad_clicks}}'), all(594, '{{pso_ad_cpa}}'),
    all(596, '{{pso_ad_audience}}'), all(603, '{{pso_ad_format}}'),
  ],
  pso_creative: [
    alt(665, 'pso_cr_table'),
    prefix(667, 'Recommendation: ', '{{pso_cr_reco}}'),
    all(670, '{{pso_conv}}'), all(673, '{{pso_cr_best_cpc}}'), all(676, '{{pso_cr_best_cpa}}'),
    ...top(680, 'pso_cr_top'),
    metric(685, '{{pso_cr_top_format}}'), metric(686, '{{pso_cr_top_ctr}}'),
    metric(687, '{{pso_cr_top_conv}}'), metric(688, '{{pso_cr_top_cpa}}'),
  ],
  pso_audience: [
    all(707, '{{pso_conv}}'), all(709, '{{pso_aud_best_cpc}}'), all(711, '{{pso_aud_best_cpa}}'),
    ...titled(749, 'pso_aud_take_1'), ...titled(750, 'pso_aud_take_2'),
    ...titled(751, 'pso_aud_take_3'), ...titled(752, 'pso_aud_take_4'),
    ...top(755, 'pso_aud_top'),
    alt(757, 'pso_aud_table'),
  ],
  pso_placement: [
    all(778, '{{pso_conv}}'), all(781, '{{pso_pl_best_cpa}}'), all(784, '{{pso_pl_best_cpc}}'),
    ...top(787, 'pso_pl_top'),
    all(789, '{{pso_pl_summary}}'),
    para(793, 0, '{{pso_conv}}'),
    all(795, '{{pso_pl_mix_1}}'), all(797, '{{pso_pl_mix_2}}'), all(799, '{{pso_pl_mix_3}}'),
    all(801, '{{pso_pl_mix_4}}'), all(803, '{{pso_pl_mix_5}}'),
    alt(820, 'pso_pl_table'),
    ...titled(837, 'pso_pl_take_1'), ...titled(838, 'pso_pl_take_2'),
    ...titled(839, 'pso_pl_take_3'), ...titled(840, 'pso_pl_take_4'),
  ],
  pso_highlights: [
    ...pair(862, 848, 'pso_highlight_1'), ...pair(863, 859, 'pso_highlight_2'),
    ...pair(864, 851, 'pso_highlight_3'), ...pair(865, 855, 'pso_highlight_4'),
  ],
  pso_recos: [
    ...pair(873, 874, 'pso_reco_1'), ...pair(878, 879, 'pso_reco_2'),
    ...pair(883, 884, 'pso_reco_3'), ...pair(888, 889, 'pso_reco_4'),
  ],
};
