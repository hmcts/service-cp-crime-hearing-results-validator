-- Renumber validation rule ids into a single sequence across categories:
-- DR-SENT-001, DR-DISQ-002, DR-CTL-003, DR-YRO-004 (previously DR-SENT-002,
-- DR-DISQ-001, DR-CTL-001, DR-YRO-001). V1.001-V1.004 are already-applied
-- migrations and are left untouched; this migration re-keys the rows they
-- seeded rather than editing their content in place.
UPDATE validation_rule SET id = 'DR-SENT-001' WHERE id = 'DR-SENT-002';
UPDATE validation_rule SET id = 'DR-DISQ-002' WHERE id = 'DR-DISQ-001';
UPDATE validation_rule SET id = 'DR-CTL-003' WHERE id = 'DR-CTL-001';
UPDATE validation_rule SET id = 'DR-YRO-004' WHERE id = 'DR-YRO-001';
