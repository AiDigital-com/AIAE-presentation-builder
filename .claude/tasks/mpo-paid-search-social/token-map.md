# Token map: Paid Search / Paid Social slides (MPO WIP templates)

Marked up 2026-11-05 on the WIP copies (see `plan.md` → Progress):
EOC `1hOJLCNqlL0rrx6a2mTFhPrmkB38uAnDE8-lmykIjhNk`, EOM `1_uY41QFNy7MmRqsc9wlmO0u_I8lW0KRI6Tn0EqKAias`.
194 distinct tokens, extracted from the decks themselves after markup (not hand-copied).

## Conventions

- Prefixes: `ps_` Paid Search (Google Ads export), `pso_` Paid Social (Meta export). `N` is 1-based.
- **Tokens** fill tiles, headlines and takeaways via the existing global `replaceAllText` pass. Each token
  was written into the sample text's own run, so it keeps the sample's font, weight and colour.
- **Takeaway / highlight / recommendation copy** follows the existing `DeckMarkup` convention: one token per
  bullet. A bold lead is sent as `[[lead]] rest` (`DeckMarkupRequestBuilder` styles it), and `*_title` tokens
  are separate shapes.
- **Tables are not tokenised cell by cell.** Each data table carries an alt-text **title marker**. The backend
  finds the table by that title, writes every data cell by row/column, and deletes unused rows (the
  template's sample numbers stay in the WIP template and are always overwritten). The EOC Campaign table also
  holds `{{ps_camp_N_*}}` cell tokens from the first test; they are harmless and get overwritten too.
- Values are pre-formatted strings (`$36.40`, `1,248`, `4.6%`, `—` for a zero denominator), as elsewhere.
- In EOM decks the same tokens carry month-to-date (report window) values. `ps_mom_conv` / `pso_mom_conv` compare
  with the previous calendar month (`+18%`).

## Table markers (alt-text title → columns, in order)

| Marker | Slide | Rows in template | Columns |
|---|---|---|---|
| `ps_camp_table` | Search Campaign Breakdown (EOC+EOM) | 3 + TOTAL | name, impr, clicks, CTR, CPC, conv, CPA, spend |
| `ps_grp_table` | Search Group Breakdown | 15 | group, impr, clicks, CTR, CPC, conv, CPA, spend (row colour = campaign) |
| `ps_kw_table` | Search Top 10 Keywords | 10 | #, keyword, impr, clicks, CTR, CPC, conv, CPA, spend |
| `ps_pmax_table` | PMax Placement Breakdown | 5 + TOTAL | placement, impr, clicks, CTR, CPC, conv, CPA, spend, spend share (+ bar shape) |
| `pso_cr_table` | Social Creative Performance | 6 + TOTAL | creative, impr, clicks, CTR, CPC, spend, conv, CPA |
| `pso_aud_table` | Social Audience Targeting | 5 + TOTAL | audience, impr, clicks, CTR, CPC, conv, CPA, spend, spend share (+ bar shape) |
| `pso_pl_table` | Social Placement Breakdown | 5 + TOTAL | placement, impr, clicks, CTR, CPC, conv, CPA, spend |
| `ps_eom_metric_table` | EOM Search metric master | 3 (Clicks, CTR, CPC) | metric, CTD goal, CTD actual, vs goal, EOC goal |
| `pso_eom_metric_table` | EOM Social metric master | 3 (Clicks, CTR, CPC) | metric, CTD goal, CTD actual, vs goal, EOC goal |

Read the exact column order from the template table header shapes before coding the writer. The column lists
above come from the sample decks.

## Tokens

### Paid Search — totals and overview (`ps_`)

| Token | Meaning | Decks |
|---|---|---|
| `ps_spend`, `ps_imps`, `ps_clicks`, `ps_ctr`, `ps_cpc`, `ps_conv`, `ps_cpa` | channel totals (EOM: report window) | EOC+EOM (imps/clicks/ctr EOC only) |
| `ps_clicks_plan` | planned clicks (media plan Estimates, open question 2) | EOC |
| `ps_verdict` | one-line headline under "PAID SEARCH" (Claude) | EOC |
| `ps_donut_N` (1–3) | legend labels of the conversions-by-campaign donut | EOC |
| `ps_male`, `ps_female` | impression share by gender | EOC |
| `ps_male_conv`, `ps_female_conv`, `ps_male_conv_share`, `ps_female_conv_share` | conversions and share by gender | EOC+EOM |
| `ps_period_label` | "the full flight" (EOC) / "October 2026" (EOM) | EOC+EOM |

### Paid Search — breakdown slides

| Family | Slide |
|---|---|
| `ps_camp_N_name` (1–3), `ps_camp_top_conv[_name]`, `ps_camp_best_cpa[_name]`, `ps_camp_best_cpc[_name]`, `ps_camp_takeaway_N` (1–3), `ps_camp_reco` | Campaign Breakdown (`ps_camp_N_name` also labels the Group slide's campaign legend) |
| `ps_grp_callout_N` (1–3) | Group Breakdown "Top-performing groups" |
| `ps_kw_takeaway_N` (1–3) | Top 10 Keywords |
| `ps_pmax_conv`, `ps_pmax_best_cpc`, `ps_pmax_best_cpa`, `ps_pmax_top_name`, `ps_pmax_top_share`, `ps_pmax_take_N[_title]` (1–4) | PMax Placement |
| `ps_age_N_share` (1–6: 18–24 … 65+), `ps_age_top_name`, `ps_age_top_share`, `ps_aud_takeaway_N` (1–3), `ps_aud_reco` | Audience Analysis |
| `ps_ad_N_badge`, `_name`, `_campaign`, `_group`, `_url`, `_headline`, `_desc`, `_clicks`, `_ctr`, `_conv`, `_cpa` (N 1–3; N=1 also on the overview's "Top performing ad") | Top Performing Ads |
| `ps_highlight_N[_title]` (1–4) | Performance Highlights (Claude) — EOC |
| `ps_reco_N[_title]` (1–4) | Recommendations (Claude) — EOC |
| `ps_eom_worked`, `ps_eom_top_window`, `ps_eom_optimise`, `ps_eom_next_month`, `ps_mom_conv` | EOM metric master |

### Paid Social (`pso_`)

| Family | Slide |
|---|---|
| `pso_spend`, `pso_imps`, `pso_clicks`, `pso_ctr`, `pso_conv`, `pso_cpa`, `pso_clicks_plan`, `pso_verdict`, `pso_donut_N` (1–2), `pso_male`, `pso_female` | divider + overview |
| `pso_ad_conv`, `pso_ad_clicks`, `pso_ad_cpa`, `pso_ad_audience`, `pso_ad_format` | overview "Top performing ad" |
| `pso_cr_best_cpc`, `pso_cr_best_cpa`, `pso_cr_top_name`, `pso_cr_top_share`, `pso_cr_top_format`, `pso_cr_top_ctr`, `pso_cr_top_conv`, `pso_cr_top_cpa`, `pso_cr_reco` | Creative Performance |
| `pso_aud_best_cpc`, `pso_aud_best_cpa`, `pso_aud_top_name`, `pso_aud_top_share`, `pso_aud_take_N[_title]` (1–4) | Audience Targeting |
| `pso_pl_best_cpa`, `pso_pl_best_cpc`, `pso_pl_top_name`, `pso_pl_top_share`, `pso_pl_summary`, `pso_pl_mix_N` (1–5, "Reels 50.3%"), `pso_pl_take_N[_title]` (1–4) | Placement Breakdown |
| `pso_highlight_N[_title]`, `pso_reco_N[_title]` (1–4) | Highlights / Recommendations (Claude) — EOC |
| `pso_eom_worked`, `pso_eom_top_creative`, `pso_eom_optimise`, `pso_eom_next_month`, `pso_mom_conv` | EOM metric master |

## Static text rewritten (no token)

Slide subtitles that described the sample data ("Illustrative data compares…") now read neutrally, e.g. "Performance
by ad group within each campaign, ranked by conversions." Labels (`#1`, age bands, "TOP 10 KEYWORDS",
"Observation / Expected Impact / Action Taken" on the EOM dividers) are unchanged.

## Not tokenised — backend / phase D follow-ups

1. **Charts are static images**: Search overview (monthly clicks & CTR, monthly spend, conversions-by-campaign
   donut), Social overview (same three, audience-type donut), Social placement conversion-mix donut. Phase D swaps
   them for linked Sheets charts.
2. **Bar shapes**: Search audience age bars and gender bar (14 groups on the Audience slide), PMax and Social
   audience "spend share" bars inside the tables. These need width updates (`updatePageElementTransform`) from the
   values.
3. **Best-value highlights**: the sample tables colour some "best" cells blue (e.g. best CPA). That styling is
   static, so the writer should reset the data cells to the regular style and highlight the best per column.
4. **Creative images**: the Social overview ad preview (3 images) and the Creative #1 card photo show the sample
   resort ad. The export has no creative assets, so hide them or show a neutral placeholder.
5. Search overview "Ad format: Responsive search ad" and "Performance" pill stay static (the export has no format
   column; Search ads are RSAs).
