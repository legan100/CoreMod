package de.niconagel.coremod.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Eigene Texte für Vanilla-Kick-Nachrichten. Eingehängt per Mixin in alle disconnect()-Methoden. */
public final class DisconnectMessages {

    /** Wie PlayerList.BAN_DATE_FORMAT, so steht das Ablaufdatum in Vanillas Ban-Nachricht. */
    private static final String VANILLA_DATE = "yyyy-MM-dd 'at' HH:mm:ss z";

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
            // Kick durch Vanillas /ban, ohne Grund
            case "multiplayer.disconnect.banned" -> screenBanned(null, null);
            // Login eines gebannten Spielers: Grund als Argument, Ablaufdatum als angehängter Text
            case "multiplayer.disconnect.banned.reason" -> screenBanned(arg(translatable), expiration(reason));
            default -> reason;
        };
    }

    public static Component banScreen(@Nullable String reason, @Nullable Date expires) {
        return screenBanned(reason, expires == null ? null : format(expires));
    }

    /** @param until null = permanent */
    private static Component screenBanned(@Nullable String reason, @Nullable String until) {
        return Component.empty()
                .append(Tablist.title())
                .append("\n\n")
                .append(Component.literal("Du wurdest von diesem Server gebannt.").withStyle(ChatFormatting.RED))
                .append("\n\n")
                .append(Component.literal("Grund: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(reason == null || reason.isBlank() ? "Kein Grund angegeben" : reason).withStyle(ChatFormatting.WHITE))
                .append("\n")
                .append(Component.literal("Dauer: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(until == null ? "Permanent" : "bis " + until).withStyle(ChatFormatting.WHITE))
                .append("\n\n")
                .append(Component.literal("Einspruch auf unserem Discord Server!").withStyle(ChatFormatting.GRAY));
    }

    private static Component screen(String reason, String hint) {
        return Component.empty()
                .append(Tablist.title())
                .append("\n\n")
                .append(Component.literal(reason).withStyle(ChatFormatting.WHITE))
                .append("\n")
                .append(Component.literal(hint).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static String arg(TranslatableContents translatable) {
        if (translatable.getArgs().length == 0) return null;
        Object arg = translatable.getArgs()[0];
        return arg instanceof Component component ? component.getString() : String.valueOf(arg);
    }

    @Nullable
    private static String expiration(Component reason) {
        for (Component sibling : reason.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents translatable
                    && translatable.getKey().equals("multiplayer.disconnect.banned.expiration")) {
                String date = arg(translatable);
                try {
                    return format(new SimpleDateFormat(VANILLA_DATE).parse(date));
                } catch (ParseException | NullPointerException e) {
                    return date;
                }
            }
        }
        return null;
    }

    private static String format(Date date) {
        return new SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'").format(date);
    }
}
