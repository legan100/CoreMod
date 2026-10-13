package de.niconagel.coremod.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.niconagel.coremod.utils.filemanager.FileManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionCache {

    private static final String API = "https://tfs-in-der-blockwelt.themineattack.de/";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<UUID, PlayerPermission> CACHE = new ConcurrentHashMap<>();
    private static final Path PRIVATE_KEY = Path.of("CoreMod/client_private.pem");

    private PermissionCache() {
    }

    public static void load(UUID uuid, String username) throws IOException, InterruptedException {
        SetupManager setupManager = SetupManager.getInstance();
        if (setupManager == null) throw new IllegalStateException("SetupManager wurde noch nicht initialisiert.");

        FileManager config = setupManager.getRestFileManager();
        if (config == null) throw new IllegalStateException("api.yml wurde noch nicht geladen.");

        String apiKey = config.getYamlValue("apikey");
        String publicKey = config.getYamlValue("client-public-key");

        if (apiKey == null || apiKey.equalsIgnoreCase("null") || apiKey.isBlank()) throw new IOException("Kein API-Key in CoreMod/api.yml.");
        if (publicKey == null || publicKey.equalsIgnoreCase("null") || publicKey.isBlank()) throw new IOException("Kein Client-Public-Key in CoreMod/api.yml.");

        publicKey = publicKey.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n").trim();

        if (!publicKey.startsWith("-----BEGIN PUBLIC KEY-----")) throw new IOException("Client-Public-Key besitzt keinen gültigen PEM-Header.");
        if (!publicKey.endsWith("-----END PUBLIC KEY-----")) throw new IOException("Client-Public-Key besitzt keinen gültigen PEM-Footer.");

        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getBytes(StandardCharsets.UTF_8));

        String url;
        if (uuid != null) {
            url = API + "api/community/users?uuid=" + URLEncoder.encode(uuid.toString(), StandardCharsets.UTF_8);
        } else if (username != null && !username.isBlank()) {
            url = API + "api/community/users?user=" + URLEncoder.encode(username, StandardCharsets.UTF_8);
        } else {
            throw new IOException("Es wurde weder eine UUID noch ein Spielername angegeben.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .header("X-Client-Public-Key", publicKeyBase64)
                .header("User-Agent", "CoreMod/1.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) throw new IOException("API HTTP " + response.statusCode() + ": " + response.body());

        PlayerPermission permission = parsePlayerData(response.body());

        if (uuid != null) CACHE.put(uuid, permission);
    }

    public static void load(UUID uuid) throws IOException, InterruptedException {
        if (uuid == null) throw new IOException("Es wurde keine UUID angegeben.");

        SetupManager setupManager = SetupManager.getInstance();
        if (setupManager == null) throw new IllegalStateException("SetupManager wurde noch nicht initialisiert.");

        FileManager config = setupManager.getRestFileManager();
        if (config == null) throw new IllegalStateException("api.yml wurde noch nicht geladen.");

        String apiKey = config.getYamlValue("apikey");
        String publicKey = config.getYamlValue("client-public-key");

        if (apiKey == null || apiKey.equalsIgnoreCase("null") || apiKey.isBlank()) throw new IOException("Kein API-Key in CoreMod/api.yml.");
        if (publicKey == null || publicKey.equalsIgnoreCase("null") || publicKey.isBlank()) throw new IOException("Kein Client-Public-Key in CoreMod/api.yml.");

        publicKey = publicKey.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n").trim();

        if (!publicKey.startsWith("-----BEGIN PUBLIC KEY-----")) throw new IOException("Client-Public-Key besitzt keinen gültigen PEM-Header.");
        if (!publicKey.endsWith("-----END PUBLIC KEY-----")) throw new IOException("Client-Public-Key besitzt keinen gültigen PEM-Footer.");

        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getBytes(StandardCharsets.UTF_8));
        String url = API + "api/community/users?uuid=" + URLEncoder.encode(uuid.toString(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .header("X-Client-Public-Key", publicKeyBase64)
                .header("User-Agent", "CoreMod/1.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) throw new IOException("API HTTP " + response.statusCode() + ": " + response.body());

        PlayerPermission permission = parsePlayerData(response.body());
        CACHE.put(uuid, permission);
    }

    public static CompletableFuture<Void> loadAsync(UUID uuid, String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                load(uuid, username);
            } catch (Exception e) {
                System.err.println("[PermissionCache] Fehler beim Laden für " + username + " / " + uuid);
                e.printStackTrace();
            }
        });
    }

    public static CompletableFuture<Void> loadAsync(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                load(uuid);
            } catch (Exception e) {
                System.err.println("[PermissionCache] Fehler beim Laden für " + uuid);
                e.printStackTrace();
            }
        });
    }

    private static PlayerPermission parsePlayerData(String response) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();

            if (root.has("encrypted") && root.get("encrypted").getAsBoolean()) {
                root = JsonParser.parseString(decrypt(root.getAsJsonObject("payload"))).getAsJsonObject();
            }

            JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;

            String username = getJsonString(data, "username");
            String role = getJsonString(data, "role");
            String apiUuid = getJsonString(data, "uuid");

            if (role == null || role.isBlank()) role = "Spieler";

            return new PlayerPermission(username, apiUuid, role);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Ungültige API-Antwort.", e);
        }
    }

    private static String decrypt(JsonObject payload) throws IOException, GeneralSecurityException {
        if (!Files.exists(PRIVATE_KEY)) {
            throw new IOException("Die API-Antwort ist verschlüsselt, aber " + PRIVATE_KEY + " fehlt.");
        }

        PrivateKey key;
        try {
            key = ResponseCrypto.readPrivateKey(Files.readString(PRIVATE_KEY, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException(PRIVATE_KEY + " ist kein gültiger Private Key (erwartet: -----BEGIN PRIVATE KEY-----).", e);
        }

        return ResponseCrypto.decrypt(
                key,
                payload.get("encrypted_key").getAsString(),
                payload.get("iv").getAsString(),
                payload.get("data").getAsString(),
                payload.get("tag").getAsString()
        );
    }

    private static String getJsonString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return null;
        return object.get(key).getAsString();
    }

    public static String getRole(UUID uuid) {
        PlayerPermission permission = CACHE.get(uuid);
        return permission == null ? "Spieler" : permission.role();
    }

    public static PlayerPermission get(UUID uuid) {
        return CACHE.get(uuid);
    }

    public static boolean contains(UUID uuid) {
        return CACHE.containsKey(uuid);
    }

    public static void remove(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static Set<UUID> getCachedPlayers() {
        return Set.copyOf(CACHE.keySet());
    }

    public record PlayerPermission(String username, String apiUuid, String role) {
    }
}
