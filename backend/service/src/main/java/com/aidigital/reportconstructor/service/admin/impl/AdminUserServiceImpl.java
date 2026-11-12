package com.aidigital.reportconstructor.service.admin.impl;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.domain.admin.repositories.AdminUserRepository;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import com.aidigital.reportconstructor.service.admin.mappers.AdminUserEntityMapper;
import com.aidigital.reportconstructor.service.common.time.CurrentTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Default {@link AdminUserService} — the only bean that injects {@link AdminUserRepository}.
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

	private final AdminUserRepository adminUsers;
	private final CurrentTime currentTime;
	private final AdminUserEntityMapper adminUserMapper;

	@Override
	@Transactional(readOnly = true)
	public boolean exists(String email) {
		return email != null && adminUsers.existsByEmailIgnoreCase(normalize(email));
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminUserEntity> listAll() {
		return adminUsers.findAllByOrderByCreatedAtDesc();
	}

	@Override
	@Transactional
	public AdminUserEntity add(String email, String addedByEmail) {
		String normalized = normalize(email);
		return adminUsers.findByEmailIgnoreCase(normalized).orElseGet(() -> adminUsers.save(adminUserMapper.toEntity(
				normalized,
				addedByEmail == null ? null : normalize(addedByEmail),
				currentTime.nowOffsetDateTime())));
	}

	@Override
	@Transactional
	public boolean removeByEmail(String email) {
		return adminUsers.findByEmailIgnoreCase(normalize(email)).map(entity -> {
			adminUsers.delete(entity);
			return true;
		}).orElse(false);
	}

	/**
	 * Trims and lowercases an email for stable storage and comparison.
	 *
	 * @param email raw email
	 * @return normalized email
	 */
	String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
