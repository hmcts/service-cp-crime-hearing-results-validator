INSERT INTO validation_rule (id, enabled, severity)
VALUES ('DR-COEW-005', true, 'ERROR');

-- DR-DISQ-001 and DR-CTL-001 were seeded disabled (V1.002 / V1.003) and have since been enabled
-- manually in every long-lived environment (SIT, PRD). Align fresh databases with that state so
-- new STE deployments no longer need a manual enable step. No-op where already enabled.
UPDATE validation_rule SET enabled = true WHERE id IN ('DR-DISQ-001', 'DR-CTL-001');
