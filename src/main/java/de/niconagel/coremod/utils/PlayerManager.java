package de.niconagel.coremod.utils;

import net.minecraft.world.entity.player.Player;

public class PlayerManager {

    private final Player p;

    public PlayerManager(Player p) {
        this.p = p;
    }

    public PlayerManager getInstance() {
        return this;
    }

    public Player getPlayer() {
        return p;
    }

    public String getUUID() {
        return p.getUUID().toString();
    }

    public String getShortUUID() {
        return getUUID().replace("-", "");
    }

    public String getUsername() {
        return p.getGameProfile().getName();
    }

    public PermissionCache.PlayerPermission getPermission() {
        return PermissionCache.get(p.getUUID());
    }

    public boolean hasPermissionData() {
        return PermissionCache.contains(p.getUUID());
    }

    public String getRole() {
        return PermissionCache.getRole(p.getUUID());
    }

    public String getLpGroup() {
        return PermissionCache.getLpGroup(p.getUUID());
    }

    public boolean hasGroup(String group) {
        return PermissionCache.hasGroup(p.getUUID(), group);
    }

    public String getDiscordId() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? null : permission.discordId();
    }

    public int getApiId() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? -1 : permission.apiId();
    }

    public boolean isWhitelisted() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission != null && permission.whitelisted();
    }

    public boolean isStatsPublic() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission != null && permission.statsPublic();
    }

    public String getTimezone() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? null : permission.timezone();
    }

    public int getBoxesManaged() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? 0 : permission.boxesManaged();
    }

    public double getHoursDriven() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? 0.0 : permission.hoursDriven();
    }

    public int getTrainsDriven() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? 0 : permission.trainsDriven();
    }

    public String getBanReason() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? null : permission.banReason();
    }

    public String getBanType() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? "none" : permission.banType();
    }

    public String getBannedUntil() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? null : permission.bannedUntil();
    }

    public boolean isBanned() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission != null && permission.banned();
    }

    public String getWarningMessage() {
        PermissionCache.PlayerPermission permission = getPermission();
        return permission == null ? null : permission.warningMessage();
    }
}
