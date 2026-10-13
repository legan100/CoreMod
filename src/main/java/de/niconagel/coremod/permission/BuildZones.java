package de.niconagel.coremod.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunks, in denen bestimmte Spieler bauen dürfen. Gilt für alle ohne {@code Permissions.BUILD_ANYWHERE}, z.B. Builder.
 * Gespeichert in CoreMod/buildzones.json als "dimension;chunkX;chunkZ" → Spieler-UUIDs.
 */
public final class BuildZones {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("CoreMod/buildzones.json");
    private static final Type TYPE = new TypeToken<Map<String, Set<UUID>>>() {}.getType();

    private static final Map<String, Set<UUID>> ZONES = new ConcurrentHashMap<>();

    private BuildZones() {
    }

    public record Chunk(String dimension, int x, int z) {
        String key() {
            return dimension + ";" + x + ";" + z;
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;

            Map<String, Set<UUID>> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE);
            if (loaded != null) {
                loaded.forEach((chunk, players) -> ZONES.put(chunk, newSet(players)));
            }
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("[CoreMod] " + FILE + " konnte nicht geladen werden", e);
        }
    }

    public static boolean allows(ResourceKey<Level> dimension, BlockPos pos, UUID player) {
        Set<UUID> players = ZONES.get(new Chunk(dimension.location().toString(), pos.getX() >> 4, pos.getZ() >> 4).key());
        return players != null && players.contains(player);
    }

    public static synchronized void add(Collection<Chunk> chunks, List<UUID> players) throws IOException {
        for (Chunk chunk : chunks) {
            ZONES.computeIfAbsent(chunk.key(), key -> newSet(List.of())).addAll(players);
        }
        save();
    }

    /** Ohne Spieler wird die Bauzone ganz gelöscht, sonst nur diese Spieler entfernt. */
    public static synchronized void remove(Collection<Chunk> chunks, List<UUID> players) throws IOException {
        for (Chunk chunk : chunks) {
            if (players.isEmpty()) {
                ZONES.remove(chunk.key());
            } else {
                ZONES.computeIfPresent(chunk.key(), (key, set) -> {
                    set.removeAll(players);
                    return set.isEmpty() ? null : set;
                });
            }
        }
        save();
    }

    private static void save() throws IOException {
        Files.writeString(FILE, GSON.toJson(ZONES, TYPE), StandardCharsets.UTF_8);
    }

    private static Set<UUID> newSet(Collection<UUID> players) {
        Set<UUID> set = ConcurrentHashMap.newKeySet();
        set.addAll(players);
        return set;
    }
}
