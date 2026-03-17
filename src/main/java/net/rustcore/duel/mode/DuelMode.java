package net.rustcore.duel.mode;

import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.modification.Modification;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Defines the contract for a duel mode.
 * Each mode controls how players are equipped, what happens each round,
 * and how winners are determined.
 */
public interface DuelMode {

    /** Internal ID used for storage, commands, etc. */
    String getId();

    /** MiniMessage display name */
    String getDisplayName();

    /** Description shown in mode selection */
    String getDescription();

    /** Icon material for menus */
    Material getIcon();

    /** Whether this mode is enabled */
    boolean isEnabled();

    /** Available best-of options (e.g. [1, 3, 5]) */
    List<Integer> getAvailableBestOf();

    /** Default best-of value */
    int getDefaultBestOf();

    /** Whether players re-draft each round */
    boolean isRedraftEachRound();

    /** Get modifications for this mode */
    Modification getModification();

    /**
     * Called when a player is teleported to the arena for a round.
     * Mode should set up the player's inventory, open menus, etc.
     */
    void onRoundSetup(Duel duel, Player player);

    /**
     * Called when a player signals they are ready (e.g. clicked ready button).
     * Not all modes need this (some may auto-ready).
     */
    void onPlayerReady(Duel duel, Player player);

    /**
     * Called when a round starts (after countdown).
     */
    void onRoundStart(Duel duel);

    /**
     * Called when a player dies during a round.
     * @return true if this death should count as a round loss
     */
    boolean onPlayerDeath(Duel duel, Player dead, Player killer);

    /**
     * Called when the duel ends entirely.
     */
    void onDuelEnd(Duel duel);

    /**
     * Reload configuration for this mode.
     */
    void reload();
}
