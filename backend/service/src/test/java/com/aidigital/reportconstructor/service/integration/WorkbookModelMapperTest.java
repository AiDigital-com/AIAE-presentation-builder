package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceAgeRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceSegmentRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookCreativeRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookCreativeTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookDeviceRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookDeviceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoRow;
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
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbookModelMapperTest {

	@Test
	void shouldDeclareTheSameComponentsAsTheServiceRecordsTest() {
		// Given: each workbook copy paired with the service record it stands for
		Map<Class<?>, Class<?>> pairs = new LinkedHashMap<>();
		pairs.put(WorkbookPublisherRow.class, PublisherRow.class);
		pairs.put(WorkbookCreativeTable.class, CreativeTable.class);
		pairs.put(WorkbookCreativeRow.class, CreativeRow.class);
		pairs.put(WorkbookGeoTable.class, GeoTable.class);
		pairs.put(WorkbookGeoRow.class, GeoRow.class);
		pairs.put(WorkbookDeviceTable.class, DeviceTable.class);
		pairs.put(WorkbookDeviceRow.class, DeviceRow.class);
		pairs.put(WorkbookAudienceTable.class, AudienceTable.class);
		pairs.put(WorkbookAudienceAgeRow.class, AudienceAgeRow.class);
		pairs.put(WorkbookAudienceSegmentRow.class, AudienceSegmentRow.class);

		// When-Then: same component names in the same order, so a new field on one side fails here
		pairs.forEach((copy, original) -> assertThat(
				Arrays.stream(copy.getRecordComponents()).map(RecordComponent::getName).toList())
				.as("%s vs %s", copy.getSimpleName(), original.getSimpleName())
				.isEqualTo(Arrays.stream(original.getRecordComponents()).map(RecordComponent::getName).toList()));
	}

	@Test
	void shouldMapEveryTableReadBackTest() {
		// Given:
		WorkbookModelMapper mapper = new WorkbookModelMapper();
		Map<Integer, WorkbookDeviceTable> devices = new LinkedHashMap<>();
		devices.put(2, new WorkbookDeviceTable("1.2%", "80%", "3", "Mobile", "61%",
				List.of(new WorkbookDeviceRow("Mobile", "1,000", "1.2%", "80%", "$10"))));
		devices.put(1, WorkbookDeviceTable.EMPTY);

		// When:
		Map<Integer, DeviceTable> mapped = mapper.deviceTables(devices);

		// Then: values, order and empty blocks carry over
		assertThat(mapped.keySet()).containsExactly(2, 1);
		assertThat(mapped.get(2)).isEqualTo(new DeviceTable("1.2%", "80%", "3", "Mobile", "61%",
				List.of(new DeviceRow("Mobile", "1,000", "1.2%", "80%", "$10"))));
		assertThat(mapped.get(1)).isEqualTo(DeviceTable.EMPTY);
		assertThat(mapper.creativeTables(Map.of(1, new WorkbookCreativeTable("6", "1%", "0.5%", "Ad",
				List.of(new WorkbookCreativeRow("Ad", "100", "1%", "", "$5"))))))
				.isEqualTo(Map.of(1, new CreativeTable("6", "1%", "0.5%", "Ad",
						List.of(new CreativeRow("Ad", "100", "1%", "", "$5")))));
		assertThat(mapper.geoTables(Map.of(1, new WorkbookGeoTable("12", "NY", "1%",
				List.of(new WorkbookGeoRow("NY", "500", "1%"))))))
				.isEqualTo(Map.of(1, new GeoTable("12", "NY", "1%", List.of(new GeoRow("NY", "500", "1%")))));
		assertThat(mapper.audienceTables(Map.of(1, new WorkbookAudienceTable("25-34", "55% F",
				List.of(new WorkbookAudienceAgeRow("25-34", "300")),
				List.of(new WorkbookAudienceSegmentRow("Sports", "1.4"))))))
				.isEqualTo(Map.of(1, new AudienceTable("25-34", "55% F",
						List.of(new AudienceAgeRow("25-34", "300")), List.of(new AudienceSegmentRow("Sports", "1.4")))));
		assertThat(mapper.publisherTables(Map.of(1, List.of(new WorkbookPublisherRow("YouTube", "900", "40%")))))
				.isEqualTo(Map.of(1, List.of(new PublisherRow("YouTube", "900", "40%"))));
		assertThat(mapper.deviceTables(null)).isNull();
	}
}
