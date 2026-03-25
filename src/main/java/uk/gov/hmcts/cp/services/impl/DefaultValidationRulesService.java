package uk.gov.hmcts.cp.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.services.ValidationRulesService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.List;

/**
 * Default in-memory implementation backed by the discovered validation rule beans.
 */
@Service
@Slf4j
public class DefaultValidationRulesService implements ValidationRulesService {

    private final List<ValidationRule> rules;

    public DefaultValidationRulesService(@Qualifier("validationRules") final List<ValidationRule> rules) {
        this.rules = rules;
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
        log.info("Getting validation rule detail for ruleId={}", sanitize(ruleId));
        return rules.stream()
                .map(ValidationRule::getRuleDetail)
                .filter(r -> ruleId.equals(r.getRuleId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Rule not found: " + ruleId));
    }

    private static String sanitize(final String value) {
        return value == null ? "" : value.replaceAll("[\r\n]", "");
    }
}
