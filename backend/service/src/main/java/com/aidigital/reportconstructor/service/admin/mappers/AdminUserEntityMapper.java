package com.aidigital.reportconstructor.service.admin.mappers;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.common.mapping.ServiceMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;

/**
 * Builds {@link AdminUserEntity} rows for UI-managed admin grants.
 */
@Mapper(config = ServiceMapperConfig.class)
public interface AdminUserEntityMapper {

	/**
	 * Creates a new admin grant. Values arrive already normalized; the id is assigned on save.
	 *
	 * @param email        normalized email being granted admin access
	 * @param addedByEmail normalized email of the admin who granted it, or {@code null}
	 * @param createdAt    when the grant was made
	 * @return a transient entity ready to save
	 */
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "email", source = "email")
	@Mapping(target = "addedByEmail", source = "addedByEmail")
	@Mapping(target = "createdAt", source = "createdAt")
	AdminUserEntity toEntity(String email, String addedByEmail, OffsetDateTime createdAt);
}
