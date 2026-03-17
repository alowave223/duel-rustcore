package net.rustcore.duel.stats;

/**
 * Holds stats for a single player in a single mode.
 */
public class PlayerStats {

    private int wins;
    private int losses;
    private int kills;
    private int deaths;
    private int winStreak;
    private int bestWinStreak;
    private int elo;

    public double getKdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    public double getWinRate() {
        int total = wins + losses;
        return total == 0 ? 0 : (double) wins / total * 100;
    }

    /**
     * Returns an immutable snapshot of the current stats for safe async use.
     */
    public synchronized PlayerStats snapshot() {
        PlayerStats copy = new PlayerStats();
        copy.wins = this.wins;
        copy.losses = this.losses;
        copy.kills = this.kills;
        copy.deaths = this.deaths;
        copy.winStreak = this.winStreak;
        copy.bestWinStreak = this.bestWinStreak;
        copy.elo = this.elo;
        return copy;
    }

    public synchronized int getWins() { return wins; }
    public synchronized void setWins(int wins) { this.wins = wins; }

    public synchronized int getLosses() { return losses; }
    public synchronized void setLosses(int losses) { this.losses = losses; }

    public synchronized int getKills() { return kills; }
    public synchronized void setKills(int kills) { this.kills = kills; }

    public synchronized int getDeaths() { return deaths; }
    public synchronized void setDeaths(int deaths) { this.deaths = deaths; }

    public synchronized int getWinStreak() { return winStreak; }
    public synchronized void setWinStreak(int winStreak) { this.winStreak = winStreak; }

    public synchronized int getBestWinStreak() { return bestWinStreak; }
    public synchronized void setBestWinStreak(int bestWinStreak) { this.bestWinStreak = bestWinStreak; }

    public synchronized int getElo() { return elo; }
    public synchronized void setElo(int elo) { this.elo = elo; }
}
