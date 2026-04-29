ALTER TABLE duels_stats DROP COLUMN elo;
DROP INDEX IF EXISTS idx_duels_stats_elo ON duels_stats;
