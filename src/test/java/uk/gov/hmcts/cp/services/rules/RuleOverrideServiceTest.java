package uk.gov.hmcts.cp.services.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Unit tests for {@link RuleOverrideService}.
 */
class RuleOverrideServiceTest {

    @Mock
    private ValidationRuleRepository ruleRepository;

    @InjectMocks
    private RuleOverrideService ruleOverrideService;

    /**
     * Verifies the service returns a populated override when the repository contains a row for the
     * requested rule id.
     */
    @Test
    void findOverride_should_return_entity_when_found() {
        ValidationRuleEntity entity = ValidationRuleEntity.builder()
                .id("DR-SENT-002")
                .enabled(false)
                .severity("WARNING")
                .updatedAt(Instant.now())
                .build();
        when(ruleRepository.findById("DR-SENT-002")).thenReturn(Optional.of(entity));

        Optional<ValidationRuleEntity> result = ruleOverrideService.findOverride("DR-SENT-002");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("DR-SENT-002");
        assertThat(result.get().isEnabled()).isFalse();
    }

    /**
     * Verifies the absence of an override is surfaced as an empty optional.
     */
    @Test
    void findOverride_should_return_empty_when_not_found() {
        when(ruleRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        Optional<ValidationRuleEntity> result = ruleOverrideService.findOverride("UNKNOWN");

        assertThat(result).isEmpty();
    }

    /**
     * Verifies repository failures are swallowed so rule evaluation can continue with YAML defaults.
     */
    @Test
    void findOverride_should_return_empty_when_db_throws() {
        when(ruleRepository.findById("DR-SENT-002")).thenThrow(new RuntimeException("DB error"));

        Optional<ValidationRuleEntity> result = ruleOverrideService.findOverride("DR-SENT-002");

        assertThat(result).isEmpty();
    }
}
