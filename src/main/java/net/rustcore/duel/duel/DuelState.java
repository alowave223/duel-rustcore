package net.rustcore.duel.duel;

public enum DuelState {
    /** Waiting for arena to be prepared */
    PREPARING,
    /** Players are selecting their kit / loading in */
    DRAFTING,
    /** Countdown before round starts */
    COUNTDOWN,
    /** Round is actively being played */
    ACTIVE,
    /** Round just ended, showing results */
    ROUND_ENDING,
    /** Duel is completely over */
    ENDED
}
