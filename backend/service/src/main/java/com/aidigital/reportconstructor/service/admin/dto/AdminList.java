package com.aidigital.reportconstructor.service.admin.dto;

import java.util.List;

/**
 * The current admins for the "Manage admins" list: config (root) entries first, then managed ones.
 *
 * @param admins the admin entries
 */
public record AdminList(List<AdminEntry> admins) {
}
