CREATE TABLE validation_rule (
                                 id          VARCHAR(20)     PRIMARY KEY,
                                 enabled     BOOLEAN         NOT NULL DEFAULT true,
                                 severity    VARCHAR(20)     NOT NULL DEFAULT 'ERROR',
                                 updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_by  VARCHAR(100)
);

INSERT INTO validation_rule (id, enabled, severity)
VALUES ('DR-SENT-002', true, 'ERROR');
