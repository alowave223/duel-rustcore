package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.kit.KitLayout;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.FixedKitMode;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class KitLayoutCommand implements CommandExecutor {

    public static final String EDITOR_TITLE_PREFIX = "Kit Layout: ";

    private final DuelsPlugin plugin;

    public KitLayoutCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.kitlayout")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("kitlayout-usage"))));
            return true;
        }

        String sub = args[0].toLowerCase();
        String modeId = args[1];
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("invalid-mode"))));
            return true;
        }
        if (!(mode instanceof FixedKitMode fixed)) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("kitlayout-not-fixed"))));
            return true;
        }

        switch (sub) {
            case "edit" -> openEditor(player, fixed);
            case "reset" -> {
                plugin.getKitLayoutManager().resetLayout(player.getUniqueId(), fixed.getId());
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("kitlayout-reset"), "{mode}", fixed.getDisplayName())));
            }
            case "show" -> showLayout(player, fixed);
            default -> player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("kitlayout-usage"))));
        }
        return true;
    }

    private void openEditor(Player player, FixedKitMode fixed) {
        String title = EDITOR_TITLE_PREFIX + fixed.getId();
        Inventory inv = Bukkit.createInventory(player, 9, CC.parse(title));

        KitLayout existing = plugin.getKitLayoutManager().getLayout(player.getUniqueId(), fixed.getId());
        for (int origSlot = 0; origSlot < 9; origSlot++) {
            ItemStack item = fixed.getHotbarItem(origSlot);
            if (item == null) continue;
            int target = existing != null ? existing.remapSlot(origSlot) : origSlot;
            if (target < 0 || target >= 9) target = origSlot;
            // If target collides with another non-null original slot already placed, fall back to origSlot
            if (inv.getItem(target) != null) target = origSlot;
            inv.setItem(target, item.clone());
        }
        player.openInventory(inv);
    }

    private void showLayout(Player player, FixedKitMode fixed) {
        KitLayout layout = plugin.getKitLayoutManager().getLayout(player.getUniqueId(), fixed.getId());
        if (layout == null || layout.getRaw().isEmpty()) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("kitlayout-show-default"), "{mode}", fixed.getDisplayName())));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("kitlayout-show-header"), "{mode}", fixed.getDisplayName())));
        for (Map.Entry<Integer, Integer> e : layout.getRaw().entrySet()) {
            player.sendMessage(CC.parse(plugin.getMessage("kitlayout-show-entry"),
                    "{orig}", String.valueOf(e.getKey()),
                    "{target}", String.valueOf(e.getValue())));
        }
    }
}
