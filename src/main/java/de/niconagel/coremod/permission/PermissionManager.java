package de.niconagel.coremod.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import de.niconagel.coremod.api.Group;
import de.niconagel.coremod.api.Job;
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
import java.util.function.Function;
import java.util.function.Predicate;

import static de.niconagel.coremod.api.Permissions.*;

public final class PermissionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Map<Group, Set<String>> permissions = Map.of();
    private static volatile Map<Job, Set<String>> jobPermissions = Map.of();

    private PermissionManager() {
    }

    /**
     * Lädt die Rechte je Rolle (permissions.json) und je Job (jobs.json). Fehlt eine Datei, wird sie mit Standardwerten angelegt.
     * Im Spiel gilt beides zusammen: die Rolle legt fest, welche Jobs man wählen darf, der Job, was man darin tun darf.
     */
    public static void load(Path groupFile, Path jobFile) {
        permissions = read(groupFile, groupDefaults(), Group::byName, new EnumMap<>(Group.class));
        jobPermissions = read(jobFile, jobDefaults(), Job::byName, new EnumMap<>(Job.class));
    }

    private static <K> Map<K, Set<String>> read(Path file, Map<String, List<String>> defaults,
                                                Function<String, K> byName, Map<K, Set<String>> into) {
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, GSON.toJson(defaults), StandardCharsets.UTF_8);
            }

            Map<String, List<String>> config = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<String>>>() {}.getType()
            );

            config.forEach((name, perms) -> {
                K key = byName.apply(name);
                if (key == null) {
                    LOGGER.warn("[CoreMod] Unbekannter Eintrag in {}: {}", file, name);
                } else {
                    into.put(key, Set.copyOf(perms));
                }
            });
            return into;

        } catch (IOException | JsonParseException | NullPointerException e) {
            throw new IllegalStateException("[CoreMod] " + file + " konnte nicht geladen werden", e);
        }
    }

    public static Group getGroup(UUID uuid) {
        Group group = Group.byName(PermissionCache.getRole(uuid));
        return group == null ? Group.GAST : group;
    }

    /** Rechte der Rolle plus die des aktuellen Jobs. Im Hauptmenü (noch kein Job) nur die der Rolle. */
    public static boolean has(UUID uuid, String permission) {
        Job job = MainMenu.getJob(uuid);
        return Permissions.matches(getPermissions(getGroup(uuid)), permission)
                || job != null && Permissions.matches(getPermissions(job), permission);
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

    public static Set<String> getPermissions(Job job) {
        return jobPermissions.getOrDefault(job, Set.of());
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

    /** Rollen: Kommunikation und welche Jobs man wählen darf. Alles fürs Spiel selbst kommt vom Job. */
    private static Map<String, List<String>> groupDefaults() {
        List<String> base = List.of(
                command("help"), command("list"), command("msg"), command("tell"), command("w"), command("me"),
                command("teammsg"), command("tm"), command("trigger"), COMMAND_COLOR, COMMAND_GROUP, CHAT,
                Job.ZUSCHAUER.getPermission(), Job.BUILDER.getPermission(), Job.TF.getPermission(), Job.FDL.getPermission()
        );
        List<String> team = new ArrayList<>(base);
        team.add(Job.TEAM.getPermission());

        Map<String, List<String>> defaults = new LinkedHashMap<>();
        for (Group group : Group.values()) {
            defaults.put(group.getDisplayName(), group == Group.DEVELOPER || group == Group.CHEF ? team : base);
        }
        return defaults;
    }

    private static Map<String, List<String>> jobDefaults() {
        List<String> spectator = List.of(MOVE, GAMEMODE_ADVENTURE, GAMEMODE_SPECTATOR, command("gamemode"));

        List<String> player = List.of(
                MOVE, INTERACT_BLOCK, INTERACT_ENTITY, CONTAINER, MOUNT, ITEM_USE, ITEM_DROP, ITEM_PICKUP,
                ATTACK_ENTITY, GAMEMODE_ADVENTURE, GAMEMODE_SURVIVAL
        );

        List<String> builder = new ArrayList<>(player);
        builder.addAll(List.of(BLOCK_BREAK, BLOCK_PLACE, ENTITY_PLACE, FLY, GAMEMODE_CREATIVE, COMMAND_FLYSPEED,
                command("gamemode"), command("tp"), command("teleport")));

        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put(Job.ZUSCHAUER.getDisplayName(), spectator);
        defaults.put(Job.BUILDER.getDisplayName(), builder);
        // ponytail: TF und FDL vorerst wie normale Spieler, eigene Rechte kommen mit ihrer Spiel-Logik
        defaults.put(Job.TF.getDisplayName(), player);
        defaults.put(Job.FDL.getDisplayName(), player);
        defaults.put(Job.TEAM.getDisplayName(), List.of(ALL));
        return defaults;
    }
}
