package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookCreativeTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookDeviceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPublisherRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps the tables read back from a workbook (the Google integration's copies) onto the service's types.
 * Each copy has the same components as its service type; {@code WorkbookModelMapperTest} pins that. Map
 * order is kept; a {@code null} map stays {@code null}.
 */
@Component
public class WorkbookModelMapper {

	/**
	 * Maps the publisher tables.
	 *
	 * @param tables tactic → publisher rows, as read
	 * @return the same tables in service terms
	 */
	public Map<Integer, List<PublisherRow>> publisherTables(Map<Integer, List<WorkbookPublisherRow>> tables) {
		return byTactic(tables, rows -> rows == null ? null : rows.stream().map(this::publisherRow).toList());
	}

	/**
	 * Maps the creative tables.
	 *
	 * @param tables tactic → creative table, as read
	 * @return the same tables in service terms
	 */
	public Map<Integer, CreativeTable> creativeTables(Map<Integer, WorkbookCreativeTable> tables) {
		return byTactic(tables, this::creativeTable);
	}

	/**
	 * Maps the geo tables.
	 *
	 * @param tables tactic → geo table, as read
	 * @return the same tables in service terms
	 */
	public Map<Integer, GeoTable> geoTables(Map<Integer, WorkbookGeoTable> tables) {
		return byTactic(tables, this::geoTable);
	}

	/**
	 * Maps the audience tables.
	 *
	 * @param tables tactic → audience table, as read
	 * @return the same tables in service terms
	 */
	public Map<Integer, AudienceTable> audienceTables(Map<Integer, WorkbookAudienceTable> tables) {
		return byTactic(tables, this::audienceTable);
	}

	/**
	 * Maps the device tables.
	 *
	 * @param tables tactic → device table, as read
	 * @return the same tables in service terms
	 */
	public Map<Integer, DeviceTable> deviceTables(Map<Integer, WorkbookDeviceTable> tables) {
		return byTactic(tables, this::deviceTable);
	}

	/**
	 * Maps one publisher row.
	 *
	 * @param row the row as read
	 * @return the row in service terms
	 */
	public PublisherRow publisherRow(WorkbookPublisherRow row) {
		return new PublisherRow(row.name(), row.impressions(), row.shareOfVoice());
	}

	/**
	 * Maps one creative table.
	 *
	 * @param table the table as read; may be {@code null}
	 * @return the table in service terms, or {@code null}
	 */
	public CreativeTable creativeTable(WorkbookCreativeTable table) {
		if (table == null) {
			return null;
		}
		return new CreativeTable(table.creativesLive(), table.bestKpi(), table.avgKpi(), table.topCreative(),
				table.rows().stream()
						.map(r -> new CreativeRow(r.name(), r.impressions(), r.ctr(), r.vcr(), r.spend()))
						.toList());
	}

	/**
	 * Maps one geo table.
	 *
	 * @param table the table as read; may be {@code null}
	 * @return the table in service terms, or {@code null}
	 */
	public GeoTable geoTable(WorkbookGeoTable table) {
		if (table == null) {
			return null;
		}
		return new GeoTable(table.marketsActivated(), table.topGeo(), table.topKpi(),
				table.rows().stream().map(r -> new GeoRow(r.name(), r.impressions(), r.kpi())).toList());
	}

	/**
	 * Maps one audience table.
	 *
	 * @param table the table as read; may be {@code null}
	 * @return the table in service terms, or {@code null}
	 */
	public AudienceTable audienceTable(WorkbookAudienceTable table) {
		if (table == null) {
			return null;
		}
		return new AudienceTable(table.ageDistribution(), table.genderDemographics(),
				table.ageRows().stream().map(r -> new AudienceAgeRow(r.ageGroup(), r.impressions())).toList(),
				table.segmentRows().stream()
						.map(r -> new AudienceSegmentRow(r.segment(), r.affinityIndex()))
						.toList());
	}

	/**
	 * Maps one device table.
	 *
	 * @param table the table as read; may be {@code null}
	 * @return the table in service terms, or {@code null}
	 */
	public DeviceTable deviceTable(WorkbookDeviceTable table) {
		if (table == null) {
			return null;
		}
		return new DeviceTable(table.highestCtr(), table.bestCompletion(), table.devicesTracked(),
				table.topDevice(), table.topDeviceImpressionsPct(),
				table.rows().stream()
						.map(r -> new DeviceRow(r.device(), r.impressions(), r.ctr(), r.vcr(), r.spend()))
						.toList());
	}

	/**
	 * Maps every value of a per-tactic map, keeping the tactic order.
	 *
	 * @param byTactic tactic → value, as read; may be {@code null}
	 * @param mapping  how to map one value
	 * @param <S>      the value type as read
	 * @param <T>      the value type in service terms
	 * @return the mapped map, or {@code null}
	 */
	<S, T> Map<Integer, T> byTactic(Map<Integer, S> byTactic, Function<S, T> mapping) {
		if (byTactic == null) {
			return null;
		}
		Map<Integer, T> mapped = new LinkedHashMap<>();
		byTactic.forEach((tacticNum, value) -> mapped.put(tacticNum, mapping.apply(value)));
		return mapped;
	}
}
