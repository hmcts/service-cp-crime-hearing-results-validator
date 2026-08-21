package uk.gov.hmcts.cp.services.impl;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.exceptions.InvalidRuleUpdateException;
import uk.gov.hmcts.cp.exceptions.RuleNotFoundException;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.UpdateRuleRequest;
import uk.gov.hmcts.cp.services.ValidationRulesService;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

/**
 * Default implementation backed by the discovered validation rule beans and the DB override table.
 */
@Service
@Slf4j
public class DefaultValidationRulesService implements ValidationRulesService {

    private final List<ValidationRule> rules;
    private final RuleOverrideService ruleOverrideService;

    /** Creates the service with the given list of discovered validation rules and override service. */
    public DefaultValidationRulesService(
            @Qualifier("validationRules") final List<ValidationRule> rules,
            final RuleOverrideService ruleOverrideService) {
        this.rules = rules;
        this.ruleOverrideService = ruleOverrideService;
    }

    /**
     * Builds a list response from the registered rules.
     *
     * @return rule list including total and enabled counts
     */
    @Override
    public RuleListResponse listRules() {
        log.info("Listing {} validation rules", rules.size());
        final List<RuleDetailResponse> ruleDetails = rules.stream()
                .map(ValidationRule::getRuleDetail)
                .toList();

        final long enabledCount = ruleDetails.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .count();

        return RuleListResponse.builder()
                .count(ruleDetails.size())
                .enabledCount((int) enabledCount)
                .rules(ruleDetails)
                .build();
    }

    /**
     * Looks up a rule by identifier and returns its current detail view.
     *
     * @param ruleId identifier of the rule to return
     * @return matching rule detail
     */
    @Override
    public RuleDetailResponse getRuleById(final String ruleId) {
        final RuleDetailResponse found = findRuleDetail(ruleId);
        log.info("Getting validation rule detail for ruleId={}", found.getRuleId());
        return found;
    }

    private RuleDetailResponse findRuleDetail(final String ruleId) {
        return rules.stream()
                .map(ValidationRule::getRuleDetail)
                .filter(r -> ruleId.equals(r.getRuleId()))
                .findFirst()
                .orElseThrow(() -> new RuleNotFoundException(ruleId));
    }

    /**
     * Partially updates a rule's enabled status and/or severity override in the database.
     *
     * <p>Applied as a single atomic upsert ({@link RuleOverrideService#applyPartialUpdate})
     * rather than a read-modify-write against a (possibly stale, per-pod-cached) entity: the
     * latter lets two concurrent or cross-pod PATCHes touching different fields silently revert
     * each other's change (DD-43134).
     *
     * @param ruleId    identifier of the rule to update
     * @param request   partial update — at least one field must be non-null
     * @param updatedBy caller identity for the audit column
     * @return updated rule detail merging YAML metadata with the new persisted override
     */
    @Override
    public RuleDetailResponse updateRule(
            final String ruleId,
            final UpdateRuleRequest request,
            final String updatedBy) {

        if (request.getEnabled() == null && request.getSeverity() == null) {
            throw new InvalidRuleUpdateException(
                    "At least one of 'enabled' or 'severity' must be provided");
        }

        final RuleDetailResponse currentDetail = findRuleDetail(ruleId);

        ruleOverrideService.applyPartialUpdate(
                ruleId,
                request.getEnabled(),
                request.getSeverity() != null ? request.getSeverity().getValue() : null,
                Boolean.TRUE.equals(currentDetail.getEnabled()),
                currentDetail.getSeverity().getValue(),
                Instant.now(),
                updatedBy);

        log.info("Updated validation rule ruleId={}", currentDetail.getRuleId());
        return getRuleById(ruleId);
    }
}
