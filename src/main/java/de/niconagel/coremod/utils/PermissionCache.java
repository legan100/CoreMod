package de.niconagel.coremod.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.niconagel.coremod.utils.filemanager.FileManager;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionCache {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Map<UUID, PlayerPermission> CACHE =
            new ConcurrentHashMap<>();

    private static final Path PRIVATE_KEY =
            Path.of("CoreMod/client_private.pem");

    private PermissionCache() {
    }

    public static void load(String shortUuid, String username)
            throws IOException, InterruptedException {

        if (shortUuid == null || shortUuid.isBlank()) {
            throw new IOException("Es wurde keine UUID angegeben.");
        }

        String id = shortUuid.trim().replace("-", "");

        if (id.length() != 32) {
            throw new IOException("Ungültige UUID: " + shortUuid);
        }

        loadInternal(uuid(id), id, username);
    }

    public static void load(UUID uuid)
            throws IOException, InterruptedException {

        if (uuid == null) {
            throw new IOException("Es wurde keine UUID angegeben.");
        }

        loadInternal(uuid, uuid.toString().replace("-", ""), null);
    }

    private static void loadInternal(
            UUID uuid,
            String shortUuid,
            String username
    ) throws IOException, InterruptedException {

        SetupManager setup = SetupManager.getInstance();

        if (setup == null) {
            throw new IllegalStateException(
                    "SetupManager wurde noch nicht initialisiert."
            );
        }

        FileManager config = setup.getRestFileManager();

        if (config == null) {
            throw new IllegalStateException(
                    "api.yml wurde noch nicht geladen."
            );
        }

        String apiKey = required(config, "apikey");
        String host = normalizeHost(required(config, "host"));
        String publicKey = normalizePublicKey(
                required(config, "client-public-key")
        );

        int port = 443;
        String portValue = config.getYamlValue("port");

        if (portValue != null
                && !portValue.isBlank()
                && !portValue.equalsIgnoreCase("null")) {
            try {
                port = Integer.parseInt(portValue.trim());
            } catch (NumberFormatException e) {
                throw new IOException(
                        "Ungültiger API-Port: " + portValue, e
                );
            }
        }

        if (port < 1 || port > 65535) {
            throw new IOException("Ungültiger API-Port: " + port);
        }

        String publicKeyBase64 = Base64.getEncoder().encodeToString(
                publicKey.getBytes(StandardCharsets.UTF_8)
        );

        String url = buildBaseUrl(host, port)
                + "/api/community/users?uuid="
                + java.net.URLEncoder.encode(
                shortUuid, StandardCharsets.UTF_8
        );

        System.out.println("[PermissionCache] API-Anfrage: " + url);

        HttpRequest request;

        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("X-API-Key", apiKey)
                    .header("X-Client-Public-Key", publicKeyBase64)
                    .header("User-Agent", "CoreMod/1.0")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige API-URL: " + url, e);
        }

        HttpResponse<String> response;

        try {
            response = HTTP.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IOException("API-Request Timeout: " + url, e);
        } catch (ConnectException e) {
            throw new IOException(
                    "API konnte nicht erreicht werden: " + url, e
            );
        } catch (UnknownHostException e) {
            throw new IOException(
                    "API-Host konnte nicht aufgelöst werden: " + host, e
            );
        } catch (IOException e) {
            throw new IOException(
                    "HTTP-Fehler beim Zugriff auf die API: "
                            + e.getMessage(),
                    e
            );
        }

        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            String body = response.body() == null
                    ? ""
                    : response.body();

            if (body.length() > 1000) {
                body = body.substring(0, 1000) + "...";
            }

            throw new IOException(
                    "API HTTP " + status + " bei " + url
                            + " | Antwort: " + body
            );
        }

        String body = response.body();

        if (body == null || body.isBlank()) {
            throw new IOException("API lieferte eine leere Antwort.");
        }

        if (isEncrypted(body)) {
            body = decryptResponse(body);
        }

        PlayerPermission permission = parsePlayerData(body);

        CACHE.put(uuid, permission);

        System.out.println(
                "[PermissionCache] Permissions erfolgreich geladen"
        );
        System.out.println(
                "[PermissionCache] Spieler: "
                        + (permission.username() != null
                        ? permission.username()
                        : username != null ? username : "unbekannt")
        );
        System.out.println("[PermissionCache] UUID: " + uuid);
        System.out.println(
                "[PermissionCache] Short-UUID: " + shortUuid
        );
        System.out.println(
                "[PermissionCache] Rolle: " + permission.role()
        );
        System.out.println(
                "[PermissionCache] Gruppe: " + permission.lpGroup()
        );
    }

    private static String decryptResponse(String response)
            throws IOException {

        try {
            JsonObject root = JsonParser.parseString(response)
                    .getAsJsonObject();

            JsonObject payload = root.getAsJsonObject("payload");

            if (payload == null) {
                throw new IOException(
                        "Verschlüsselte Antwort enthält kein payload."
                );
            }

            String encryptedKey = requiredJson(payload, "encrypted_key");
            String iv = requiredJson(payload, "iv");
            String tag = requiredJson(payload, "tag");
            String data = requiredJson(payload, "data");

            PrivateKey privateKey = loadPrivateKey();

            byte[] encryptedAesKey =
                    Base64.getDecoder().decode(encryptedKey);
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            byte[] tagBytes = Base64.getDecoder().decode(tag);
            byte[] cipherBytes = Base64.getDecoder().decode(data);

            Cipher rsa = Cipher.getInstance(
                    "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
            );
            rsa.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] aesKey = rsa.doFinal(encryptedAesKey);

            byte[] combined = new byte[
                    cipherBytes.length + tagBytes.length
                    ];

            System.arraycopy(
                    cipherBytes, 0,
                    combined, 0,
                    cipherBytes.length
            );

            System.arraycopy(
                    tagBytes, 0,
                    combined, cipherBytes.length,
                    tagBytes.length
            );

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");

            aes.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(128, ivBytes)
            );

            return new String(
                    aes.doFinal(combined),
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Fehler beim Entschlüsseln der API-Antwort: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private static PrivateKey loadPrivateKey() throws IOException {
        Path path = PRIVATE_KEY;

        if (!Files.exists(path)) {
            throw new IOException(
                    "Private Key nicht gefunden: " + path
            );
        }

        try {
            String pem = Files.readString(path, StandardCharsets.UTF_8)
                    .replace("\\n", "\n")
                    .replace("\r", "")
                    .trim();

            String content = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            if (content.isBlank()) {
                throw new IOException(
                        "Private Key enthält keinen Inhalt."
                );
            }

            byte[] encoded = Base64.getDecoder().decode(content);

            return KeyFactory.getInstance("RSA")
                    .generatePrivate(
                            new PKCS8EncodedKeySpec(encoded)
                    );

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Private Key konnte nicht geladen werden.", e
            );
        }
    }

    private static String requiredJson(
            JsonObject object,
            String key
    ) throws IOException {

        if (object == null
                || !object.has(key)
                || object.get(key).isJsonNull()) {
            throw new IOException(
                    "Verschlüsselte Antwort enthält kein '" + key + "'."
            );
        }

        String value = object.get(key).getAsString();

        if (value == null || value.isBlank()) {
            throw new IOException(
                    "Verschlüsseltes Feld '" + key + "' ist leer."
            );
        }

        return value;
    }

    private static boolean isEncrypted(String response) {
        try {
            JsonObject root = JsonParser.parseString(response)
                    .getAsJsonObject();

            return root.has("encrypted")
                    && !root.get("encrypted").isJsonNull()
                    && root.get("encrypted").getAsBoolean();

        } catch (Exception e) {
            return false;
        }
    }

    private static PlayerPermission parsePlayerData(String response)
            throws IOException {

        try {
            JsonObject root = JsonParser.parseString(response)
                    .getAsJsonObject();

            JsonObject data = root.has("data")
                    && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data")
                    : root;

            String username = first(data, root, "username");
            String role = first(data, root, "role");
            String group = first(data, root, "lp_group");
            String uuidValue = first(data, root, "uuid");

            JsonObject stats = object(data, "stats");
            JsonObject status = object(data, "status");

            if (role == null || role.isBlank()) {
                role = "Spieler";
            }

            if (group == null || group.isBlank()) {
                group = "default";
            }

            return new PlayerPermission(
                    username,
                    normalizeUuid(uuidValue),
                    integer(data, root, "id", -1),
                    first(data, root, "discord_id"),
                    role,
                    group,
                    bool(data, "is_whitelisted", false),
                    bool(data, "stats_public", false),
                    str(data, "timezone"),
                    integer(stats, null, "boxes_managed", 0),
                    decimal(stats, null, "hours_driven", 0),
                    integer(stats, null, "trains_driven", 0),
                    str(status, "ban_reason"),
                    valueOr(str(status, "ban_type"), "none"),
                    str(status, "banned_until"),
                    bool(status, "is_banned", false),
                    str(status, "warning_message")
            );

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Ungültige API-Antwort.", e);
        }
    }

    private static String first(
            JsonObject primary,
            JsonObject fallback,
            String key
    ) {
        String value = str(primary, key);
        return value != null ? value : str(fallback, key);
    }

    private static JsonObject object(
            JsonObject object,
            String key
    ) {
        return object != null
                && object.has(key)
                && object.get(key).isJsonObject()
                ? object.getAsJsonObject(key)
                : null;
    }

    private static UUID normalizeUuid(String value)
            throws IOException {

        if (value == null || value.isBlank()) {
            return null;
        }

        value = value.trim().replace("-", "");

        if (value.length() != 32) {
            throw new IOException(
                    "Ungültige UUID von der API: " + value
            );
        }

        return uuid(value);
    }

    private static UUID uuid(String value) throws IOException {
        try {
            return UUID.fromString(
                    value.substring(0, 8) + "-"
                            + value.substring(8, 12) + "-"
                            + value.substring(12, 16) + "-"
                            + value.substring(16, 20) + "-"
                            + value.substring(20)
            );
        } catch (Exception e) {
            throw new IOException("Ungültige UUID: " + value, e);
        }
    }

    private static String normalizePublicKey(String key)
            throws IOException {

        key = key.replace("\\n", "\n")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        String header = "-----BEGIN PUBLIC KEY-----";
        String footer = "-----END PUBLIC KEY-----";

        if (!key.startsWith(header)) {
            throw new IOException(
                    "Client-Public-Key besitzt keinen gültigen Header."
            );
        }

        if (!key.endsWith(footer)) {
            throw new IOException(
                    "Client-Public-Key besitzt keinen gültigen Footer."
            );
        }

        String content = key.substring(
                        header.length(),
                        key.length() - footer.length()
                )
                .replaceAll("\\s", "");

        if (content.isBlank()) {
            throw new IOException(
                    "Client-Public-Key enthält keinen Base64-Inhalt."
            );
        }

        try {
            Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Client-Public-Key enthält ungültige Base64-Daten.",
                    e
            );
        }

        return key;
    }

    private static String normalizeHost(String host)
            throws IOException {

        host = host.trim();

        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        if (host.startsWith("http://")) {
            throw new IOException(
                    "HTTP ist nicht erlaubt. Bitte HTTPS verwenden."
            );
        }

        if (!host.startsWith("https://")) {
            host = "https://" + host;
        }

        try {
            if (URI.create(host).getHost() == null) {
                throw new IOException("Ungültiger API-Host: " + host);
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültiger API-Host: " + host, e);
        }

        return host;
    }

    private static String buildBaseUrl(String host, int port) {
        return port == 443 ? host : host + ":" + port;
    }

    private static String required(
            FileManager config,
            String key
    ) throws IOException {

        String value = config.getYamlValue(key);

        if (value == null
                || value.isBlank()
                || value.equalsIgnoreCase("null")) {
            throw new IOException(
                    "Kein " + key + " in CoreMod/api.yml."
            );
        }

        return value;
    }

    private static String str(JsonObject object, String key) {
        if (object == null
                || !object.has(key)
                || object.get(key).isJsonNull()) {
            return null;
        }

        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(
            JsonObject primary,
            JsonObject fallback,
            String key,
            int def
    ) {
        try {
            if (primary != null
                    && primary.has(key)
                    && !primary.get(key).isJsonNull()) {
                return primary.get(key).getAsInt();
            }

            if (fallback != null
                    && fallback.has(key)
                    && !fallback.get(key).isJsonNull()) {
                return fallback.get(key).getAsInt();
            }

        } catch (Exception ignored) {
        }

        return def;
    }

    private static double decimal(
            JsonObject primary,
            JsonObject fallback,
            String key,
            double def
    ) {
        try {
            if (primary != null
                    && primary.has(key)
                    && !primary.get(key).isJsonNull()) {
                return primary.get(key).getAsDouble();
            }

            if (fallback != null
                    && fallback.has(key)
                    && !fallback.get(key).isJsonNull()) {
                return fallback.get(key).getAsDouble();
            }

        } catch (Exception ignored) {
        }

        return def;
    }

    private static boolean bool(
            JsonObject object,
            String key,
            boolean def
    ) {
        try {
            return object == null
                    || !object.has(key)
                    || object.get(key).isJsonNull()
                    ? def
                    : object.get(key).getAsBoolean();

        } catch (Exception e) {
            return def;
        }
    }

    public static CompletableFuture<Void> loadAsync(
            String shortUuid,
            String username
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                load(shortUuid, username);
            } catch (Exception e) {
                System.err.println(
                        "[PermissionCache] Fehler beim asynchronen Laden"
                );
                System.err.println(
                        "[PermissionCache] Spieler: " + username
                );
                System.err.println(
                        "[PermissionCache] UUID: " + shortUuid
                );
                System.err.println(
                        "[PermissionCache] Ursache: " + e.getMessage()
                );
                e.printStackTrace();
            }
        });
    }

    public static CompletableFuture<Void> loadAsync(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                load(uuid);
            } catch (Exception e) {
                System.err.println(
                        "[PermissionCache] Fehler beim asynchronen Laden"
                );
                System.err.println(
                        "[PermissionCache] UUID: " + uuid
                );
                System.err.println(
                        "[PermissionCache] Ursache: " + e.getMessage()
                );
                e.printStackTrace();
            }
        });
    }

    public static boolean hasGroup(UUID uuid, String group) {
        if (uuid == null || group == null || group.isBlank()) {
            return false;
        }

        PlayerPermission permission = CACHE.get(uuid);

        return permission != null
                && permission.lpGroup() != null
                && permission.lpGroup().equalsIgnoreCase(group);
    }

    public static String getRole(UUID uuid) {
        PlayerPermission permission = CACHE.get(uuid);
        return permission == null ? "Spieler" : permission.role();
    }

    public static PlayerPermission get(UUID uuid) {
        return CACHE.get(uuid);
    }

    public static PlayerPermission getPermission(UUID uuid) {
        return CACHE.get(uuid);
    }

    public static boolean contains(UUID uuid) {
        return CACHE.containsKey(uuid);
    }

    public static void remove(UUID uuid) {
        if (uuid != null) {
            CACHE.remove(uuid);
        }
    }

    public static Set<UUID> getCachedPlayers() {
        return Set.copyOf(CACHE.keySet());
    }

    public record PlayerPermission(
            String username,
            UUID apiUuid,
            int apiId,
            String discordId,
            String role,
            String lpGroup,
            boolean whitelisted,
            boolean statsPublic,
            String timezone,
            int boxesManaged,
            double hoursDriven,
            int trainsDriven,
            String banReason,
            String banType,
            String bannedUntil,
            boolean banned,
            String warningMessage
    ) {
    }
}