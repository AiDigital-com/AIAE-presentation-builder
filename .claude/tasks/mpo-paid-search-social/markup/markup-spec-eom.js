// EOM-only token markup (ids are suffixes of h7b47cb0403f57878_1_), plus the campaign-slide text edits that
// were made by hand on the EOC deck (ids are EOC suffixes, mapped to EOM through the id map).
const all = (id, tok) => ({ id, mode: 'all', tok });
const para = (id, i, tok) => ({ id, mode: 'para', i, tok });
const prefix = (id, prefix, tok) => ({ id, mode: 'prefix', prefix, tok });
const alt = (id, title) => ({ id, mode: 'alt', title });

module.exports = {
  // EOC-numbered, use with --idmap
  ps_campaign_text: [
    all(91, 'Delivery and conversion efficiency by campaign across {{ps_period_label}}.'),
    all(95, '{{ps_camp_top_conv}} · {{ps_camp_top_conv_name}}'),
    all(98, '{{ps_camp_best_cpa}} · {{ps_camp_best_cpa_name}}'),
    all(101, '{{ps_camp_best_cpc}} · {{ps_camp_best_cpc_name}}'),
    para(121, 0, '{{ps_camp_takeaway_1}}'), para(121, 1, '{{ps_camp_takeaway_2}}'), para(121, 2, '{{ps_camp_takeaway_3}}'),
    prefix(123, 'Recommendation: ', '{{ps_camp_reco}}'),
  ],
  // EOM-numbered, no id map
  ps_eom_metric: [
    para(21, 1, '{{ps_eom_worked}}'), para(23, 1, '{{ps_eom_top_window}}'), para(25, 1, '{{ps_eom_optimise}}'),
    alt(30, 'ps_eom_metric_table'),
    all(36, '{{ps_spend}}'), all(39, '{{ps_conv}}'), all(42, '{{ps_cpa}}'),
    para(45, 1, '{{ps_mom_conv}}'),
    all(48, '{{ps_eom_next_month}}'),
  ],
  pso_eom_metric: [
    para(446, 1, '{{pso_eom_worked}}'), para(448, 1, '{{pso_eom_top_creative}}'), para(450, 1, '{{pso_eom_optimise}}'),
    alt(454, 'pso_eom_metric_table'),
    all(457, '{{pso_spend}}'), all(460, '{{pso_conv}}'), all(463, '{{pso_cpa}}'),
    para(465, 1, '{{pso_mom_conv}}'),
    all(468, '{{pso_eom_next_month}}'),
  ],
};
