package uk.gov.hmcts.cp.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.services.ValidationRulesService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultValidationRulesService implements ValidationRulesService {

    private final List<ValidationRule> rules;

    @Override
    public RuleListResponse listRules() {
        log.info("Listing {} validation rules", rules.size());
        List<RuleDetailResponse> ruleDetails = rules.stream()
                .map(ValidationRule::getRuleDetail)
                .toList();

        long enabledCount = ruleDetails.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .count();

        return RuleListResponse.builder()
                .count(ruleDetails.size())
                .enabledCount((int) enabledCount)
                .rules(ruleDetails)
                .build();
    }

    @Override
    public RuleDetailResponse getRuleById(String ruleId) {
        log.info("Getting validation rule detail for ruleId={}", ruleId);
        return rules.stream()
                .map(ValidationRule::getRuleDetail)
                .filter(r -> ruleId.equals(r.getRuleId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Rule not found: " + ruleId));
    }
}
