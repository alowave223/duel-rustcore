package net.rustcore.duel.settings;

/**
 * Minimal stub — replaced with full implementation in Task 8.
 */
public class PlayerSettings {
    public enum Visibility { ALL, FRIENDS_ONLY, NOBODY }
    public enum Status { ONLINE, OFFLINE, DO_NOT_DISTURB }

    public boolean isAcceptFriendRequests() { return true; }
    public Visibility getWhoCanInviteToParty() { return Visibility.ALL; }
    public Visibility getWhoCanChallenge() { return Visibility.ALL; }
    public Status getStatus() { return Status.ONLINE; }
}
