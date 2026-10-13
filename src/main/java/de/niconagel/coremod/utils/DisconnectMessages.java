package de.niconagel.coremod.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Eigene Texte für Vanilla-Kick-Nachrichten. Eingehängt per Mixin in alle disconnect()-Methoden. */
public final class DisconnectMessages {

    private DisconnectMessages() {
    }

    /** Ersetzt bekannte Vanilla-Nachrichten anhand ihres Übersetzungsschlüssels, alle anderen bleiben unverändert. */
    public static Component replace(Component reason) {
        if (!(reason.getContents() instanceof TranslatableContents translatable)) {
            return reason;
        }

        return switch (translatable.getKey()) {
            case "multiplayer.disconnect.server_shutdown" -> screen(
                    "Der Server wurde geschlossen.",
                    "Versuch es gleich noch einmal.");
            case "multiplayer.disconnect.not_whitelisted" -> screen(
                    "Du stehst nicht auf der Whitelist.",
                    "Weitere Infos auf unserem Discord Server!");
            default -> reason;
        };
    }

    private static Component screen(String reason, String hint) {
        return Component.empty()
                .append(Tablist.title())
                .append("\n\n")
                .append(Component.literal(reason).withStyle(ChatFormatting.WHITE))
                .append("\n")
                .append(Component.literal(hint).withStyle(ChatFormatting.GRAY));
    }
}
