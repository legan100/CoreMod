package de.niconagel.coremod.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import de.niconagel.coremod.api.Group;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.mainmenu.MainMenu;
import de.niconagel.coremod.utils.PermissionCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static de.niconagel.coremod.api.Permissions.*;

public final class PermissionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Map<Group, Set<String>> permissions = Map.of();

    private PermissionManager() {
    }

    /** Lädt die Rechte je Gruppe. Fehlt die Datei, wird sie mit Standardwerten angelegt. */
    public static void load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, GSON.toJson(defaults()), StandardCharsets.UTF_8);
            }

            Map<String, List<String>> config = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<String>>>() {}.getType()
            );

            Map<Group, Set<String>> loaded = new EnumMap<>(Group.class);
            config.forEach((name, perms) -> {
                Group group = Group.byName(name);
                if (group == null) {
                    LOGGER.warn("[CoreMod] Unbekannte Gruppe in {}: {}", file, name);
                } else {
                    loaded.put(group, Set.copyOf(perms));
                }
            });
            permissions = loaded;

        } catch (IOException | JsonParseException | NullPointerException e) {
            throw new IllegalStateException("[CoreMod] " + file + " konnte nicht geladen werden", e);
        }
    }

    public static Group getGroup(UUID uuid) {
        Group group = Group.byName(PermissionCache.getRole(uuid));
        return group == null ? Group.GAST : group;
    }

    public static boolean has(UUID uuid, String permission) {
        return Permissions.matches(getPermissions(getGroup(uuid)), permission);
    }

    /**
     * true = Aktion verbieten. Nur echte Spieler werden eingeschränkt, Fake-Player anderer Mods nie.
     * Wer im Hauptmenü ist, darf gar nichts.
     */
    public static boolean isDenied(Entity entity, String permission) {
        return entity instanceof ServerPlayer player
                && !player.isFakePlayer()
                && (MainMenu.isInMenu(player) || !has(player.getUUID(), permission));
    }

    /** Wie {@link #isDenied(Entity, String)} für Bau-Rechte: ohne {@link Permissions#BUILD_ANYWHERE} nur in freigegebenen Bauzonen. */
    public static boolean isDenied(Entity entity, String permission, BlockPos pos) {
        return isDenied(entity, permission)
                || entity instanceof ServerPlayer player
                && !player.isFakePlayer()
                && !has(player.getUUID(), BUILD_ANYWHERE)
                && !BuildZones.allows(player.level().dimension(), pos, player.getUUID());
    }

    public static Set<String> getPermissions(Group group) {
        return permissions.getOrDefault(group, Set.of());
    }

    /** Für {@code Commands.literal(...).requires(...)}. Die Konsole braucht OP-Level 4. */
    public static Predicate<CommandSourceStack> require(String permission) {
        return source -> {
            ServerPlayer player = source.getPlayer();
            return player == null ? source.hasPermission(4) : has(player.getUUID(), permission);
        };
    }

    public static boolean mayUseGameMode(UUID uuid, GameType mode) {
        return mode == fallbackGameMode(uuid) || has(uuid, "minecraft.gamemode." + mode.getName());
    }

    /** Spielmodus für Spieler, deren aktueller Modus nicht erlaubt ist. Adventure, wenn nicht mal Survival erlaubt ist. */
    public static GameType fallbackGameMode(UUID uuid) {
        return has(uuid, GAMEMODE_SURVIVAL) ? GameType.SURVIVAL : GameType.ADVENTURE;
    }

    private static Map<String, List<String>> defaults() {
        List<String> commands = List.of(
                command("help"), command("list"), command("msg"), command("tell"), command("w"), command("me"),
                command("teammsg"), command("tm"), command("trigger"), COMMAND_COLOR, COMMAND_GROUP
        );

        List<String> guest = new ArrayList<>(commands);
        guest.addAll(List.of(MOVE, CHAT, GAMEMODE_ADVENTURE));

        List<String> player = new ArrayList<>(guest);
        player.addAll(List.of(
                INTERACT_BLOCK, INTERACT_ENTITY, CONTAINER, MOUNT, ITEM_USE, ITEM_DROP, ITEM_PICKUP,
                ATTACK_ENTITY, GAMEMODE_SURVIVAL
        ));

        List<String> builder = new ArrayList<>(player);
        builder.addAll(List.of(BLOCK_BREAK, BLOCK_PLACE, ENTITY_PLACE, FLY, GAMEMODE_CREATIVE, COMMAND_FLYSPEED, command("gamemode")));

        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put(Group.GAST.getDisplayName(), guest);
        for (Group group : List.of(Group.TF_AZUBI, Group.TF, Group.FDL_AZUBI, Group.FDL)) {
            defaults.put(group.getDisplayName(), player);
        }
        defaults.put(Group.BUILDER.getDisplayName(), builder);
        defaults.put(Group.DEVELOPER.getDisplayName(), List.of(ALL));
        defaults.put(Group.CHEF.getDisplayName(), List.of(ALL));
        return defaults;
    }
}
