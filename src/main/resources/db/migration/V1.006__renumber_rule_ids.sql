-- Renumber validation rule ids into a single sequence across categories:
-- DR-SENT-001, DR-DISQ-002, DR-CTL-003, DR-YRO-004 (previously DR-SENT-002,
-- DR-DISQ-001, DR-CTL-001, DR-YRO-001). V1.001-V1.004 are already-applied
-- migrations and are left untouched; this migration re-keys the rows they
-- seeded rather than editing their content in place.
--
-- Guarded rather than a plain UPDATE: if a row already exists at the target
-- id (e.g. an STE rebuild that reseeds rows under the new ids ahead of
-- Flyway, or a PATCH-created validation_rule row landing on the new id
-- before this migration runs), a plain UPDATE ... SET id = '<new>' WHERE
-- id = '<old>' hits a primary-key violation and the service crashloops at
-- startup (flagged in the PR #154 review, DD-43134). Each pair is handled
-- defensively:
--
--   * old id present, new id absent       -> rename old id to new id
--   * old id present, new id also present -> a row already occupies the new
--                                             id; keep that row and drop the
--                                             stale old-id row
--   * old id absent                       -> nothing to renumber, no-op
DO
$$
    DECLARE
        rule_id_mapping CONSTANT text[][] := ARRAY [
            ARRAY ['DR-SENT-002', 'DR-SENT-001'],
            ARRAY ['DR-DISQ-001', 'DR-DISQ-002'],
            ARRAY ['DR-CTL-001', 'DR-CTL-003'],
            ARRAY ['DR-YRO-001', 'DR-YRO-004']
            ];
        old_id text;
        new_id text;
    BEGIN
        FOR i IN 1 .. array_length(rule_id_mapping, 1)
            LOOP
                old_id := rule_id_mapping[i][1];
                new_id := rule_id_mapping[i][2];

                IF EXISTS (SELECT 1 FROM validation_rule WHERE id = old_id) THEN
                    IF EXISTS (SELECT 1 FROM validation_rule WHERE id = new_id) THEN
                        DELETE FROM validation_rule WHERE id = old_id;
                    ELSE
                        UPDATE validation_rule SET id = new_id WHERE id = old_id;
                    END IF;
                END IF;
            END LOOP;
    END
$$;
