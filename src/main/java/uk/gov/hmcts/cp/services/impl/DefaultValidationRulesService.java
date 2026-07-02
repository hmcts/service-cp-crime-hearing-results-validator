package uk.gov.hmcts.cp.services.impl;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Rule not found: " + ruleId));
    }

    /**
     * Partially updates a rule's enabled status and/or severity override in the database.
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one of 'enabled' or 'severity' must be provided");
        }

        final RuleDetailResponse currentDetail = findRuleDetail(ruleId);

        final ValidationRuleEntity existing = ruleOverrideService.findOverride(ruleId)
                .orElseGet(() -> buildDefaultEntity(ruleId, currentDetail));

        final ValidationRuleEntity updated = ValidationRuleEntity.builder()
                .id(existing.getId())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled())
                .severity(request.getSeverity() != null
                        ? request.getSeverity().getValue()
                        : existing.getSeverity())
                .updatedAt(Instant.now())
                .updatedBy(updatedBy)
                .build();

        ruleOverrideService.saveOverride(updated);
        log.info("Updated validation rule ruleId={}", ruleId);
        return getRuleById(ruleId);
    }

    private static ValidationRuleEntity buildDefaultEntity(
            final String ruleId,
            final RuleDetailResponse currentDetail) {
        return ValidationRuleEntity.builder()
                .id(ruleId)
                .enabled(Boolean.TRUE.equals(currentDetail.getEnabled()))
                .severity(currentDetail.getSeverity().getValue())
                .build();
    }
}
