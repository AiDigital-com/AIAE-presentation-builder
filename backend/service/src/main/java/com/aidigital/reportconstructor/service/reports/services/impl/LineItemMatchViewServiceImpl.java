package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.common.security.AppUser;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemMatchViewAssembler;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatchViewService;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link LineItemMatchViewService}: delegates matching to {@link LineItemMatcherService} and
 * shapes the result with {@link LineItemMatchViewAssembler}.
 */
@Service
@RequiredArgsConstructor
public class LineItemMatchViewServiceImpl implements LineItemMatchViewService {

	private final LineItemMatcherService matcher;
	private final LineItemMatchViewAssembler assembler;

	@Override
	public LineItemMatchView matchView(List<List<String>> bqRows, List<List<String>> planRows, AppUser caller) {
		return assembler.assemble(matcher.match(bqRows, planRows, caller));
	}
}
