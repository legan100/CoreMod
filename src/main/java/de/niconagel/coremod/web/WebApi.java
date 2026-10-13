package de.niconagel.coremod.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.niconagel.coremod.permission.BuildZones;
import de.niconagel.coremod.utils.DisconnectMessages;
import de.niconagel.coremod.utils.SetupManager;
import de.niconagel.coremod.utils.filemanager.FileManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * HTTP-API auf Port 8767 für Bauzonen, Bans und Whitelist. Jede Anfrage ist ein POST mit JSON-Body
 * und braucht den Header "X-API-Key" mit dem "server-apikey" aus CoreMod/api.yml.
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class WebApi {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT = 8767;
    private static final int MAX_BODY = 1 << 20;
    /** Obergrenze je Anfrage, damit ein riesiger Bereich nicht den Server lahmlegt. */
    private static final int MAX_CHUNKS = 65_536;

    private static HttpServer http;

    private WebApi() {
    }

    private interface Action {
        String run(JsonObject body) throws Exception;
    }

    @SubscribeEvent
    public static void onStart(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        String key = apiKey();

        try {
            http = HttpServer.create(new InetSocketAddress(PORT), 0);
        } catch (IOException e) {
            LOGGER.error("[CoreMod] API konnte Port {} nicht öffnen", PORT, e);
            return;
        }

        http.createContext("/buildzone", exchange -> handle(exchange, key, WebApi::buildZone));
        http.createContext("/ban", exchange -> handle(exchange, key, body -> onServer(server, () -> ban(server, body))));
        http.createContext("/whitelist", exchange -> handle(exchange, key, body -> onServer(server, () -> whitelist(server, body))));
        http.start();
        LOGGER.info("[CoreMod] API läuft auf Port {}", PORT);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        if (http != null) http.stop(0);
    }

    /** Fehlt der Schlüssel in der api.yml, wird einer erzeugt und eingetragen. */
    private static String apiKey() {
        FileManager config = SetupManager.getInstance().getRestFileManager();
        String key = config.getYamlValue("server-apikey");
        if (key == null) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            key = HexFormat.of().formatHex(random);
            config.writeInNextFreeLine("server-apikey: " + key);
            LOGGER.info("[CoreMod] Neuer API-Schlüssel in CoreMod/api.yml (server-apikey) erzeugt");
        }
        return key;
    }

    private static void handle(HttpExchange exchange, String key, Action action) throws IOException {
        try (exchange) {
            int status = 200;
            JsonObject response = new JsonObject();
            String header = exchange.getRequestHeaders().getFirst("X-API-Key");

            if (!exchange.getRequestURI().getPath().equals(exchange.getHttpContext().getPath())) {
                status = 404;
                response.addProperty("error", "Nicht gefunden");
            } else if (!exchange.getRequestMethod().equals("POST")) {
                status = 405;
                response.addProperty("error", "Nur POST erlaubt");
            } else if (header == null || key.isBlank() || key.equals("null")
                    || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8))) {
                status = 401;
                response.addProperty("error", "Ungültiger API-Schlüssel");
            } else {
                try {
                    byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                    if (raw.length > MAX_BODY) throw new IllegalArgumentException("Anfrage zu groß");
                    response.addProperty("message", action.run(JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject()));
                } catch (RuntimeException e) {
                    // Fehlende oder falsche Felder im JSON
                    status = 400;
                    response.addProperty("error", e.getMessage() == null ? "Ungültige Anfrage" : e.getMessage());
                } catch (Exception e) {
                    LOGGER.error("[CoreMod] API-Fehler bei {}", exchange.getRequestURI(), e);
                    status = 500;
                    response.addProperty("error", "Interner Fehler");
                }
            }

            response.addProperty("success", status == 200);
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    /** Ban- und Whitelist-Listen und Spieler gehören dem Server-Thread. */
    private static String onServer(MinecraftServer server, Supplier<String> task) throws Exception {
        try {
            return server.submit(task).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    // ---------------------------------------------------------------- Bauzonen

    /**
     * {"action": "add" | "remove", "players": ["uuid", …], "dimension": "minecraft:overworld", "chunks": [{"x": 0, "z": 0}, …],
     * "from": {"x": 0, "z": 0}, "to": {"x": 5, "z": 5}}. Chunk-Koordinaten, nicht Block-Koordinaten.
     * Einzelne Chunks und Bereich lassen sich kombinieren. "remove" ohne "players" löscht die Bauzone ganz, dann darf dort niemand mehr bauen.
     */
    private static String buildZone(JsonObject body) throws IOException {
        boolean add = switch (required(body, "action")) {
            case "add" -> true;
            case "remove" -> false;
            default -> throw new IllegalArgumentException("\"action\" muss \"add\" oder \"remove\" sein");
        };

        ResourceLocation dimension = ResourceLocation.tryParse(body.has("dimension") ? body.get("dimension").getAsString() : "minecraft:overworld");
        if (dimension == null) throw new IllegalArgumentException("Ungültige Dimension");

        List<UUID> players = new ArrayList<>();
        if (body.has("players")) {
            for (JsonElement player : body.getAsJsonArray("players")) players.add(UUID.fromString(player.getAsString()));
        }
        if (add && players.isEmpty()) throw new IllegalArgumentException("Keine Spieler angegeben");

        Set<BuildZones.Chunk> chunks = new LinkedHashSet<>();
        if (body.has("chunks")) {
            for (JsonElement element : body.getAsJsonArray("chunks")) {
                JsonObject chunk = element.getAsJsonObject();
                chunks.add(new BuildZones.Chunk(dimension.toString(), chunk.get("x").getAsInt(), chunk.get("z").getAsInt()));
            }
        }
        if (body.has("from") || body.has("to")) {
            JsonObject from = body.getAsJsonObject("from");
            JsonObject to = body.getAsJsonObject("to");
            if (from == null || to == null) throw new IllegalArgumentException("Bereich braucht \"from\" und \"to\"");

            int minX = Math.min(from.get("x").getAsInt(), to.get("x").getAsInt());
            int maxX = Math.max(from.get("x").getAsInt(), to.get("x").getAsInt());
            int minZ = Math.min(from.get("z").getAsInt(), to.get("z").getAsInt());
            int maxZ = Math.max(from.get("z").getAsInt(), to.get("z").getAsInt());
            if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) throw new IllegalArgumentException("Mehr als " + MAX_CHUNKS + " Chunks");

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) chunks.add(new BuildZones.Chunk(dimension.toString(), x, z));
            }
        }
        if (chunks.isEmpty()) throw new IllegalArgumentException("Keine Chunks angegeben (\"chunks\" oder \"from\"/\"to\")");
        if (chunks.size() > MAX_CHUNKS) throw new IllegalArgumentException("Mehr als " + MAX_CHUNKS + " Chunks");

        if (add) {
            BuildZones.add(chunks, players);
            return chunks.size() + " Chunks freigegeben";
        }
        BuildZones.remove(chunks, players);
        return chunks.size() + " Chunks entfernt";
    }

    // ---------------------------------------------------------------- Ban & Whitelist

    /** {"uuid": "…", "action": "ban" | "unban", "reason": "…", "expires": "2026-12-31T23:59:00Z"}. Ohne "expires" permanent. */
    private static String ban(MinecraftServer server, JsonObject body) {
        GameProfile profile = profile(server, body);
        UserBanList bans = server.getPlayerList().getBans();

        switch (required(body, "action")) {
            case "ban" -> {
                String reason = body.has("reason") ? body.get("reason").getAsString() : "Kein Grund angegeben";
                Date expires = body.has("expires") && !body.get("expires").isJsonNull()
                        ? Date.from(Instant.parse(body.get("expires").getAsString())) : null;

                bans.add(new UserBanListEntry(profile, null, "CoreMod-API", expires, reason));
                ServerPlayer online = server.getPlayerList().getPlayer(profile.getId());
                if (online != null) online.connection.disconnect(DisconnectMessages.banScreen(reason, expires));
                return profile.getName() + " wurde gebannt";
            }
            case "unban" -> {
                if (!bans.isBanned(profile)) return profile.getName() + " war nicht gebannt";
                bans.remove(profile);
                return profile.getName() + " wurde entbannt";
            }
            default -> throw new IllegalArgumentException("\"action\" muss \"ban\" oder \"unban\" sein");
        }
    }

    /** {"uuid": "…", "action": "add" | "remove"}. */
    private static String whitelist(MinecraftServer server, JsonObject body) {
        GameProfile profile = profile(server, body);
        UserWhiteList whitelist = server.getPlayerList().getWhiteList();

        switch (required(body, "action")) {
            case "add" -> {
                whitelist.add(new UserWhiteListEntry(profile));
                return profile.getName() + " steht auf der Whitelist";
            }
            case "remove" -> {
                whitelist.remove(profile);
                // Wie Vanillas /whitelist remove: bei enforce-whitelist fliegt der Spieler sofort
                if (server.getPlayerList().isUsingWhitelist()) server.kickUnlistedPlayers(server.createCommandSourceStack());
                return profile.getName() + " steht nicht mehr auf der Whitelist";
            }
            default -> throw new IllegalArgumentException("\"action\" muss \"add\" oder \"remove\" sein");
        }
    }

    /** Name aus dem Cache des Servers, sonst aus "name" im Body, sonst die UUID. Bans und Whitelist prüfen nur die UUID. */
    private static GameProfile profile(MinecraftServer server, JsonObject body) {
        UUID uuid = UUID.fromString(required(body, "uuid"));
        String name = body.has("name") ? body.get("name").getAsString() : uuid.toString();
        return server.getProfileCache().get(uuid).orElseGet(() -> new GameProfile(uuid, name));
    }

    private static String required(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("\"" + field + "\" fehlt");
        return value.getAsString();
    }
}
