package uk.gov.hmcts.cp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "validation_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRuleEntity {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
