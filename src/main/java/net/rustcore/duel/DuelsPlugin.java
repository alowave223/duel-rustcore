package net.rustcore.duel;

import net.rustcore.duel.arena.ArenaManager;
import net.rustcore.duel.arena.SlimeArenaManager;
import net.rustcore.duel.command.DuelCommand;
import net.rustcore.duel.command.FriendCommand;
import net.rustcore.duel.command.HubCommand;
import net.rustcore.duel.command.KitLayoutCommand;
import net.rustcore.duel.command.LobbyCommand;
import net.rustcore.duel.command.PartyCommand;
import net.rustcore.duel.command.SettingsCommand;
import net.rustcore.duel.friend.FriendManager;
import net.rustcore.duel.kit.KitLayoutManager;
import net.rustcore.duel.listener.KitLayoutEditorListener;
import net.rustcore.duel.party.PartyManager;
import net.rustcore.duel.settings.SettingsManager;
import net.rustcore.duel.placeholder.DuelsExpansion;
import net.rustcore.duel.duel.DuelManager;
import net.rustcore.duel.listener.ArenaProtectionListener;
import net.rustcore.duel.listener.DuelListener;
import net.rustcore.duel.listener.KitMenuListener;
import net.rustcore.duel.listener.LobbyListener;
import net.rustcore.duel.listener.PlayerJoinQuitListener;
import net.rustcore.duel.lobby.LobbyManager;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.ModeManager;
import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.DatabaseConfig;
import net.rustcore.duel.db.MigrationService;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.FriendsDao;
import net.rustcore.duel.db.dao.KitLayoutsDao;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import net.rustcore.duel.db.dao.SettingsDao;
import net.rustcore.duel.db.dao.StatsDao;
import net.rustcore.duel.stats.StatsManager;
import net.rustcore.duel.rating.RatingConfig;
import net.rustcore.duel.rating.RatingService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class DuelsPlugin extends JavaPlugin {

    private Database database;
    private ArenaManager arenaManager;
    private ModeManager modeManager;
    private DuelManager duelManager;
    private StatsManager statsManager;
    private LobbyManager lobbyManager;
    private SlimeArenaManager slimeArenaManager;
    private FriendManager friendManager;
    private PartyManager partyManager;
    private SettingsManager settingsManager;
    private KitLayoutManager kitLayoutManager;
    private RatingService ratingService;

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
        try {
            DatabaseConfig dbConfig = DatabaseConfig.fromSection(getConfig().getConfigurationSection("database"));
            this.database = Database.forConfig(dbConfig);
            new Migrations(database.dataSource()).apply();
        } catch (Exception e) {
            getLogger().severe("Database init failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        StatsDao statsDao = new StatsDao(database.dataSource());
        FriendsDao friendsDao = new FriendsDao(database.dataSource());
        SettingsDao settingsDao = new SettingsDao(database.dataSource());
        KitLayoutsDao kitLayoutsDao = new KitLayoutsDao(database.dataSource());
        RankedPrefsDao rankedPrefsDao = new RankedPrefsDao(database.dataSource());

        duelManager = new DuelManager(this, rankedPrefsDao);
        statsManager = new StatsManager(this, statsDao);
        RatingConfig ratingCfg = RatingConfig.fromSection(getConfig().getConfigurationSection("rating"));
        ratingService = new RatingService(this, ratingCfg);
        if (ratingCfg.enabled()) {
            getLogger().info("OpenSkill rating backend enabled at " + ratingCfg.baseUrl());
        }
        lobbyManager = new LobbyManager(this);
        friendManager = new FriendManager(this, friendsDao);
        partyManager = new PartyManager(this);
        settingsManager = new SettingsManager(this, settingsDao);
        kitLayoutManager = new KitLayoutManager(this, kitLayoutsDao);

        // Load
        arenaManager.load();
        modeManager.load();
        lobbyManager.load();

        // One-shot YAML -> DB migration (no-op after first run due to *.migrated renames)
        new MigrationService(getDataFolder(), statsDao, friendsDao, settingsDao, kitLayoutsDao, rankedPrefsDao)
                .runIfNeeded(modeManager.getAllModes().stream().map(DuelMode::getId).toList());

        // Register stats for each mode
        registerModeStats();

        // Register listeners
        getServer().getPluginManager().registerEvents(new DuelListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new KitMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new KitLayoutEditorListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

        // Register commands
        DuelCommand duelCommand = new DuelCommand(this);
        getCommand("duel").setExecutor(duelCommand);
        getCommand("duel").setTabCompleter(duelCommand);
        getCommand("hub").setExecutor(new HubCommand(this));
        getCommand("lobby").setExecutor(new LobbyCommand(this));
        getCommand("f").setExecutor(new FriendCommand(this));
        getCommand("party").setExecutor(new PartyCommand(this));
        getCommand("dsettings").setExecutor(new SettingsCommand(this));
        getCommand("kitlayout").setExecutor(new KitLayoutCommand(this));

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
        if (statsManager != null) {
            statsManager.saveAll();
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

        if (database != null) {
            database.shutdown();
        }

        getLogger().info("Duels plugin disabled!");
    }

    /**
     * Register stats files for all loaded modes.
     */
    private void registerModeStats() {
        for (DuelMode mode : modeManager.getAllModes()) {
            statsManager.registerMode(mode.getId());
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

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public KitLayoutManager getKitLayoutManager() {
        return kitLayoutManager;
    }

    public RatingService getRatingService() {
        return ratingService != null ? ratingService : new RatingService(this, new RatingConfig(false, "", "", 2000, 5000));
    }
}
