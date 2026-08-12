-- Collision-safe follow-up to V1.006's rule-id renumbering.
--
-- V1.006 runs a plain UPDATE validation_rule SET id = '<new>' WHERE id = '<old>'
-- for each renumbered rule. If a row already exists at the target id (e.g. an
-- STE rebuild that reseeds rows under the new ids ahead of Flyway, or a
-- PATCH-created validation_rule row landing on the new id before this
-- migration runs), that UPDATE hits a primary-key violation and the service
-- crashloops at startup (flagged in the PR #154 review, DD-43134).
--
-- V1.006 is an already-applied migration in every environment where no
-- collision occurred, so it is left untouched here rather than edited in
-- place -- editing it would change its checksum and break Flyway validation
-- on any environment that already ran it successfully. This migration
-- re-applies the same four renumbering pairs defensively, guarding each one
-- against the collision case:
--
--   * old id present, new id absent    -> rename old id to new id (mirrors V1.006)
--   * old id present, new id also present -> a row already occupies the new id;
--                                            keep that row and drop the stale old-id row
--   * old id absent                    -> already renumbered by V1.006, no-op
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
