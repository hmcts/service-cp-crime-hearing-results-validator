INSERT INTO validation_rule (id, enabled, severity)
VALUES ('DR-COEW-005', true, 'ERROR');

-- DR-DISQ-001 and DR-CTL-001 were seeded disabled (V1.002 / V1.003) and have since been enabled
-- manually in every long-lived environment (SIT, PRD). DR-YRO-001 rows created by team-branch
-- builds predating PR #150 were also seeded disabled (V1.004 said false at the time). Align
-- databases with the enabled state we run everywhere so STE deployments no longer need a manual
-- enable step. No-op where already enabled.
UPDATE validation_rule SET enabled = true WHERE id IN ('DR-DISQ-001', 'DR-CTL-001', 'DR-YRO-001');
