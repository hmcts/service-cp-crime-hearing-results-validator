package uk.gov.hmcts.cp.services.rules.cel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionDefinition {

    private String id;
    private String name;
    private String expression;
    private String severity;
    private String messageTemplate;
    private String affectedOffenceSet;
}
