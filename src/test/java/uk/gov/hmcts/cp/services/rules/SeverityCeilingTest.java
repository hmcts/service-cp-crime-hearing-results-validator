package uk.gov.hmcts.cp.services.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityCeilingTest {

    // --- normalize ---

    @Test
    void normalize_null_returns_null() {
        assertThat(SeverityCeiling.normalize(null)).isNull();
    }

    @Test
    void normalize_ERROR_returns_ERROR() {
        assertThat(SeverityCeiling.normalize("ERROR")).isEqualTo("ERROR");
    }

    @Test
    void normalize_WARNING_returns_WARNING() {
        assertThat(SeverityCeiling.normalize("WARNING")).isEqualTo("WARNING");
    }

    @Test
    void normalize_lowercase_error_returns_ERROR() {
        assertThat(SeverityCeiling.normalize("error")).isEqualTo("ERROR");
    }

    @Test
    void normalize_lowercase_warning_returns_WARNING() {
        assertThat(SeverityCeiling.normalize("warning")).isEqualTo("WARNING");
    }

    @Test
    void normalize_invalid_value_WARN_returns_null() {
        assertThat(SeverityCeiling.normalize("WARN")).isNull();
    }

    @Test
    void normalize_whitespace_returns_null() {
        assertThat(SeverityCeiling.normalize("  ")).isNull();
    }

    // --- ordinal ---

    @Test
    void ordinal_WARNING_is_0() {
        assertThat(SeverityCeiling.ordinal("WARNING")).isEqualTo(0);
    }

    @Test
    void ordinal_ERROR_is_1() {
        assertThat(SeverityCeiling.ordinal("ERROR")).isEqualTo(1);
    }

    @Test
    void ordinal_unknown_value_treated_as_ERROR() {
        assertThat(SeverityCeiling.ordinal("UNKNOWN")).isEqualTo(1);
    }

    // --- resolve ---

    @Test
    void resolve_no_db_override_returns_yaml_severity() {
        assertThat(SeverityCeiling.resolve("ERROR", null)).isEqualTo("ERROR");
    }

    @Test
    void resolve_no_db_override_warning_returns_yaml_warning() {
        assertThat(SeverityCeiling.resolve("WARNING", null)).isEqualTo("WARNING");
    }

    @Test
    void resolve_warning_ceiling_caps_error_down_to_warning() {
        assertThat(SeverityCeiling.resolve("ERROR", "WARNING")).isEqualTo("WARNING");
    }

    @Test
    void resolve_warning_ceiling_keeps_warning_as_warning() {
        assertThat(SeverityCeiling.resolve("WARNING", "WARNING")).isEqualTo("WARNING");
    }

    @Test
    void resolve_error_ceiling_keeps_error_as_error() {
        assertThat(SeverityCeiling.resolve("ERROR", "ERROR")).isEqualTo("ERROR");
    }

    @Test
    void resolve_error_ceiling_does_not_upgrade_warning() {
        assertThat(SeverityCeiling.resolve("WARNING", "ERROR")).isEqualTo("WARNING");
    }

    @Test
    void resolve_invalid_db_value_falls_back_to_yaml_severity() {
        assertThat(SeverityCeiling.resolve("ERROR", "WARN")).isEqualTo("ERROR");
    }
}
