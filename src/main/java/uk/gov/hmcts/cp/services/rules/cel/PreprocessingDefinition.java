package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import lombok.Builder;

/**
 * YAML-backed preprocessing configuration for a CEL validation rule.
 */
@Builder
public record PreprocessingDefinition(
        String type,
        List<String> filterShortCodes,
        String groupBy,
        int skipWhenGroupCount,
        List<String> relevantOffenceCodes,
        List<String> excludedFinalShortCodes,
        List<String> extendedTestShortCodes,
        List<String> remandShortCodes,
        List<String> ctlShortCodes,

        // YRO-specific short-code lists (used by YouthRehabilitationPreprocessor)
        List<String> yroOrderShortCodes,
        List<String> curfewShortCodes,
        List<String> curfewTagShortCodes,
        List<String> furtherCurfewShortCodes,

        // Community-order-specific short-code lists (used by CommunityOrderEndDatePreprocessor;
        // the curfew* lists above are shared with YRO)
        List<String> communityOrderShortCodes,
        List<String> alcoholAbstinenceShortCodes,

        // Sexual-offence-notification-specific fields (used by
        // SexualOffenceNotificationPreprocessor)
        String qualifyingMisCode,
        List<String> adultNotificationShortCodes,
        List<String> youthNotificationShortCodes) {
}
