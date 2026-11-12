package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.GoogleApiException;
import com.aidigital.reportconstructor.externalservices.google.slides.SlidesDeckClient;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownThoughtsGate;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Implements the service's {@link SlidesProvider} port on top of the Google integration's
 * {@link SlidesDeckClient} (real or stub): maps the deck vocabulary through {@link DeckModelMapper}, applies
 * the service's {@link BreakdownThoughtsGate} before handing the qualifying tactics down, and turns a
 * {@link GoogleApiException} back into the {@link AppException} the report pipeline records, with the same
 * message.
 */
@Component
@RequiredArgsConstructor
public class SlidesProviderBridge implements SlidesProvider {

	private final SlidesDeckClient slides;
	private final DeckModelMapper mapper;
	private final BreakdownThoughtsGate thoughtsGate;

	@Override
	public boolean isLive() {
		return slides.isLive();
	}

	@Override
	public String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			TemplateVariant templateVariant, String userGoogleAccessToken) {
		return call(() -> slides.createDeck(jobId, fileName, placeholderMap, reportType,
				mapper.template(templateVariant), userGoogleAccessToken));
	}

	@Override
	public void trimTactics(
			String presentationId, int tacticCount, String reportType, TemplateVariant templateVariant,
			String userGoogleAccessToken) {
		run(() -> slides.trimTactics(presentationId, tacticCount, reportType, mapper.template(templateVariant),
				userGoogleAccessToken));
	}

	@Override
	public void addTacticSlides(
			String presentationId, int tacticCount, Map<String, String> placeholderMap, String reportType,
			TemplateVariant templateVariant, String userGoogleAccessToken) {
		run(() -> slides.addTacticSlides(presentationId, tacticCount, placeholderMap, reportType,
				mapper.template(templateVariant), userGoogleAccessToken));
	}

	@Override
	public void addBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues, String reportType, TemplateVariant templateVariant,
			String userGoogleAccessToken) {
		Set<Integer> thoughtsTactics = thoughtsGate.qualifyingTactics(enabledByTactic);
		run(() -> slides.addBreakdownSlides(presentationId, mapper.breakdownsByTactic(enabledByTactic),
				breakdownValues, thoughtsTactics, reportType, mapper.template(templateVariant),
				userGoogleAccessToken));
	}

	@Override
	public void deleteMasterSlides(
			String presentationId, String reportType, TemplateVariant templateVariant, String userGoogleAccessToken) {
		run(() -> slides.deleteMasterSlides(presentationId, reportType, mapper.template(templateVariant),
				userGoogleAccessToken));
	}

	@Override
	public void deleteReportTypeSlides(String presentationId, String reportType, String userGoogleAccessToken) {
		run(() -> slides.deleteReportTypeSlides(presentationId, reportType, userGoogleAccessToken));
	}

	@Override
	public List<String> dashUnresolvedTokens(
			String presentationId, String reportType, TemplateVariant templateVariant, String userGoogleAccessToken) {
		return call(() -> slides.dashUnresolvedTokens(presentationId, reportType, mapper.template(templateVariant),
				userGoogleAccessToken));
	}

	@Override
	public void applyDeckMarkup(String presentationId, String userGoogleAccessToken) {
		run(() -> slides.applyDeckMarkup(presentationId, userGoogleAccessToken));
	}

	@Override
	public void fillPaidSlides(String presentationId, PaidSlidesFill fill, String userGoogleAccessToken) {
		run(() -> slides.fillPaidSlides(presentationId, mapper.paidFill(fill), userGoogleAccessToken));
	}

	@Override
	public int countSlides(String presentationId, String userGoogleAccessToken) {
		return call(() -> slides.countSlides(presentationId, userGoogleAccessToken));
	}

	@Override
	public boolean carriesTacticThoughts(String reportType, TemplateVariant templateVariant) {
		return slides.carriesTacticThoughts(reportType, mapper.template(templateVariant));
	}

	@Override
	public boolean carriesMpoNarrative(String reportType, TemplateVariant templateVariant) {
		return slides.carriesMpoNarrative(reportType, mapper.template(templateVariant));
	}

	/**
	 * Runs a Slides operation that returns a value, translating its failure for the report pipeline.
	 *
	 * @param operation the client call
	 * @param <T>       the result type
	 * @return the operation's result
	 * @throws AppException {@link ErrorReason#C000} carrying the client's message when the call fails
	 */
	<T> T call(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (GoogleApiException ex) {
			throw new AppException(ErrorReason.C000, ex.getMessage());
		}
	}

	/**
	 * Runs a Slides operation without a result, translating its failure for the report pipeline.
	 *
	 * @param operation the client call
	 * @throws AppException {@link ErrorReason#C000} carrying the client's message when the call fails
	 */
	void run(Runnable operation) {
		call(() -> {
			operation.run();
			return null;
		});
	}
}
