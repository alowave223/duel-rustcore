package net.rustcore.duel.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class CC {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CC() {}

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return Component.empty();
        return MM.deserialize(miniMessage);
    }

    /**
     * Parse a MiniMessage string with key-value placeholder pairs.
     * Placeholders are inserted as unparsed (literal) text to prevent
     * player-controlled values from injecting MiniMessage tags.
     * Example: CC.parse("Hello <player>!", "{player}", player.getName())
     */
    public static Component parse(String miniMessage, String... placeholders) {
        if (miniMessage == null) return Component.empty();
        if (placeholders.length == 0) return MM.deserialize(miniMessage);

        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            // Strip the { } wrappers to get the bare tag name, e.g. "{player}" -> "player"
            String key = placeholders[i].replaceAll("[{}]", "");
            String value = placeholders[i + 1] != null ? placeholders[i + 1] : "";
            resolver.resolver(Placeholder.unparsed(key, value));
        }
        return MM.deserialize(miniMessage, resolver.build());
    }

    public static String strip(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
