package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One Claude-written field of the paid-media narrative call: the reply key, its character budget and what it says.
 *
 * @param key      the reply's JSON key and the token name without its channel prefix, e.g. {@code pmax_take_1_title}
 * @param limit    the character budget of the field's text
 * @param guidance what the field says, written for the prompt
 */
public record PaidCopyField(String key, int limit, String guidance) {
}
