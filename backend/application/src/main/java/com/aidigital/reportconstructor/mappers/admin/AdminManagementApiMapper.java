package com.aidigital.reportconstructor.mappers.admin;

import com.aidigital.reportconstructor.api.v1.model.AdminEntryV1;
import com.aidigital.reportconstructor.api.v1.model.AdminListV1;
import com.aidigital.reportconstructor.api.v1.model.AdminSourceV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.admin.dto.AdminEntry;
import com.aidigital.reportconstructor.service.admin.dto.AdminList;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps service-layer {@link AdminEntry} rows to their V1 API DTOs for the
 * "Manage admins" list.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface AdminManagementApiMapper {

	/**
	 * Converts an admin entry into its V1 DTO.
	 *
	 * @param entry the service admin entry
	 * @return the V1 admin entry
	 */
	@Mapping(target = "source", expression = "java(toSource(entry.source()))")
	AdminEntryV1 toEntry(AdminEntry entry);

	/**
	 * Converts a list of admin entries into V1 DTOs.
	 *
	 * @param entries the service admin entries
	 * @return the V1 admin entries
	 */
	List<AdminEntryV1> toEntries(List<AdminEntry> entries);

	/**
	 * Converts the current admins into the V1 list envelope.
	 *
	 * @param list the service admin list
	 * @return the V1 admin list
	 */
	AdminListV1 toList(AdminList list);

	/**
	 * Maps a source wire code to the V1 {@link AdminSourceV1} enum.
	 *
	 * @param source the service source string
	 * @return the matching source enum, or {@code null} when {@code source} is {@code null}
	 */
	default AdminSourceV1 toSource(String source) {
		return source == null ? null : AdminSourceV1.fromValue(source);
	}
}
