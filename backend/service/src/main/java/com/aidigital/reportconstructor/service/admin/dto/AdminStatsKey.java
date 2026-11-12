package com.aidigital.reportconstructor.service.admin.dto;

import java.time.OffsetDateTime;

/**
 * Identifies one cached dashboard snapshot.
 *
 * <p>The rollup version is what keeps a snapshot honest on every node, not only on the one that rebuilt the
 * rollup: a rebuild anywhere moves the version, so every node's next read misses its old entry and assembles
 * from the new rollup. The dashboard is thus never older than the rollup's staleness budget on any node.
 *
 * @param range         the window being reported on
 * @param rollupVersion when the {@code usage_daily} rollup was last refreshed, or {@code null} when unknown
 */
public record AdminStatsKey(AdminDateRange range, OffsetDateTime rollupVersion) {
}
