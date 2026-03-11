package uk.gov.hmcts.cp.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.services.ValidationRulesService;

import java.util.List;

@Service
@Slf4j
public class MockValidationRulesService implements ValidationRulesService {

    private static final RuleDetailResponse MOCK_RULE = RuleDetailResponse.builder()
            .ruleId("DR-SENT-001")
            .title("EMONE requirement for custodial sentences")
            .description("Custodial and community sentences require an accompanying EMONE result")
            .priority(1000)
            .severity(RuleDetailResponse.SeverityEnum.ERROR)
            .enabled(true)
            .build();

    @Override
    public RuleListResponse listRules() {
        log.info("Listing mock validation rules");
        return RuleListResponse.builder()
                .count(1)
                .enabledCount(1)
                .rules(List.of(MOCK_RULE))
                .build();
    }

    @Override
    public RuleDetailResponse getRuleById(String ruleId) {
        log.info("Getting mock rule detail for ruleId={}", ruleId);
        return RuleDetailResponse.builder()
                .ruleId(ruleId)
                .title(MOCK_RULE.getTitle())
                .description(MOCK_RULE.getDescription())
                .priority(MOCK_RULE.getPriority())
                .severity(MOCK_RULE.getSeverity())
                .enabled(MOCK_RULE.getEnabled())
                .build();
    }
}
