ALTER TABLE duels_stats ADD COLUMN mu             DOUBLE NOT NULL DEFAULT 25.0;
ALTER TABLE duels_stats ADD COLUMN sigma          DOUBLE NOT NULL DEFAULT 8.333333333333334;
ALTER TABLE duels_stats ADD COLUMN rating_ordinal DOUBLE NOT NULL DEFAULT 0.0;
ALTER TABLE duels_stats ADD COLUMN matches_rated  INT    NOT NULL DEFAULT 0;

CREATE INDEX idx_stats_mode_ordinal ON duels_stats (mode_id, rating_ordinal DESC);
