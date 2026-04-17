package net.rustcore.duel;

import net.rustcore.duel.arena.ArenaManager;
import net.rustcore.duel.arena.SlimeArenaManager;
import net.rustcore.duel.command.DuelCommand;
import net.rustcore.duel.command.FriendCommand;
import net.rustcore.duel.command.HubCommand;
import net.rustcore.duel.command.LobbyCommand;
import net.rustcore.duel.command.PartyCommand;
import net.rustcore.duel.friend.FriendManager;
import net.rustcore.duel.party.PartyManager;
import net.rustcore.duel.placeholder.DuelsExpansion;
import net.rustcore.duel.duel.DuelManager;
import net.rustcore.duel.listener.ArenaProtectionListener;
import net.rustcore.duel.listener.DuelListener;
import net.rustcore.duel.listener.KitMenuListener;
import net.rustcore.duel.listener.LobbyListener;
import net.rustcore.duel.lobby.LobbyManager;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.ModeManager;
import net.rustcore.duel.stats.StatsManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class DuelsPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private ModeManager modeManager;
    private DuelManager duelManager;
    private StatsManager statsManager;
    private LobbyManager lobbyManager;
    private SlimeArenaManager slimeArenaManager;
    private FriendManager friendManager;
    private PartyManager partyManager;

    @Override
    public void onEnable() {
        // Save default configs
        saveDefaultConfig();
        saveResourceIfMissing("modes/kitbuilder.yml");

        // Initialize managers
        arenaManager = new ArenaManager(this);
        slimeArenaManager = new SlimeArenaManager(this);
        if (!slimeArenaManager.init()) {
            getLogger().severe("AdvancedSlimePaper is required but failed to initialize. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("SlimeWorldManager arena system initialized.");
        modeManager = new ModeManager(this);
        duelManager = new DuelManager(this);
        statsManager = new StatsManager(this);
        lobbyManager = new LobbyManager(this);
        friendManager = new FriendManager(this);
        friendManager.load();
        partyManager = new PartyManager(this);

        // Load
        arenaManager.load();
        modeManager.load();
        lobbyManager.load();

        // Register stats for each mode
        registerModeStats();

        // Register listeners
        getServer().getPluginManager().registerEvents(new DuelListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new KitMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);

        // Register commands
        DuelCommand duelCommand = new DuelCommand(this);
        getCommand("duel").setExecutor(duelCommand);
        getCommand("duel").setTabCompleter(duelCommand);
        getCommand("hub").setExecutor(new HubCommand(this));
        getCommand("lobby").setExecutor(new LobbyCommand(this));
        getCommand("f").setExecutor(new FriendCommand(this));
        getCommand("party").setExecutor(new PartyCommand(this));

        // Register BungeeCord plugin messaging channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Register PlaceholderAPI expansion if present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DuelsExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("Duels plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Save all stats and ranked preferences
        if (statsManager != null) {
            statsManager.saveAll();
        }
        if (duelManager != null) {
            duelManager.saveRankedSync();
        }

        if (friendManager != null) {
            friendManager.save();
        }

        if (slimeArenaManager != null) {
            slimeArenaManager.destroyAll();
        }

        // End all active duels gracefully
        if (duelManager != null) {
            for (var duel : duelManager.getActiveDuels()) {
                duel.forceEnd(null);
            }
        }

        getLogger().info("Duels plugin disabled!");
    }

    /**
     * Register stats files for all loaded modes.
     */
    private void registerModeStats() {
        for (DuelMode mode : modeManager.getAllModes()) {
            // Load the mode's config to get stats settings
            File modeConfigFile = new File(getDataFolder(), "modes/" + mode.getId() + ".yml");
            if (modeConfigFile.exists()) {
                YamlConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeConfigFile);
                String statsFile = modeConfig.getString("stats.file", "stats/" + mode.getId() + "_stats.yml");
                int startingElo = modeConfig.getInt("stats.starting-elo", 1000);
                int eloKFactor = modeConfig.getInt("stats.elo-k-factor", 32);
                statsManager.registerMode(mode.getId(), statsFile, startingElo, eloKFactor);
            } else {
                statsManager.registerMode(mode.getId(), "stats/" + mode.getId() + "_stats.yml", 1000, 32);
            }
        }
    }

    /**
     * Save a resource from the jar if it doesn't exist in the data folder.
     */
    private void saveResourceIfMissing(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            saveResource(resourcePath, false);
        }
    }

    /**
     * Get a message from config with the prefix.
     */
    public String getMessage(String key) {
        return getConfig().getString("messages." + key, "<red>Missing message: " + key);
    }

    // Manager accessors
    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ModeManager getModeManager() {
        return modeManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public SlimeArenaManager getSlimeArenaManager() {
        return slimeArenaManager;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    /**
     * Temporary stub — replaced by real SettingsManager in Task 8.
     */
    public net.rustcore.duel.settings.SettingsManager getSettingsManager() {
        return null;
    }
}
