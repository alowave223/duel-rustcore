package net.rustcore.duel.settings;

public class PlayerSettings {

    public enum Visibility { ALL, FRIENDS_ONLY, NOBODY }
    public enum Status { ONLINE, OFFLINE, DO_NOT_DISTURB }

    private Visibility whoCanInviteToParty = Visibility.ALL;
    private Visibility whoCanChallenge = Visibility.ALL;
    private boolean acceptFriendRequests = true;
    private Status status = Status.ONLINE;

    public Visibility getWhoCanInviteToParty() { return whoCanInviteToParty; }
    public void setWhoCanInviteToParty(Visibility v) { this.whoCanInviteToParty = v; }

    public Visibility getWhoCanChallenge() { return whoCanChallenge; }
    public void setWhoCanChallenge(Visibility v) { this.whoCanChallenge = v; }

    public boolean isAcceptFriendRequests() { return acceptFriendRequests; }
    public void setAcceptFriendRequests(boolean b) { this.acceptFriendRequests = b; }

    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
}
