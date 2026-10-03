package de.niconagel.coremod.listener;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@EventBusSubscriber
public class RealTimeMinecraftDay {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final double LAT = 49.4521;
    private static final double LON = 11.0767;

    private static final long UPDATE_TICKS = 100;
    private static long lastTick = -1;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;

        long tick = level.getGameTime();
        if (lastTick != -1 && tick - lastTick < UPDATE_TICKS) return;

        lastTick = tick;
        update(level);
    }

    private static void update(ServerLevel level) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalDate date = now.toLocalDate();

        ZonedDateTime rise = sun(date, true);
        ZonedDateTime set = sun(date, false);

        if (rise == null || set == null) {
            normalTime(level, now);
            return;
        }

        long r = sec(rise);
        long s = sec(set);
        long n = sec(now);
        long time;

        if (n >= r && n < s) {
            time = Math.round((n - r) * 12000.0 / (s - r));
        } else {
            ZonedDateTime nextRise = n >= s
                    ? sun(date.plusDays(1), true)
                    : rise;

            if (nextRise == null) {
                normalTime(level, now);
                return;
            }

            long nr = sec(nextRise);
            long nightLength = 86400 - s + nr;
            long progress = n >= s ? n - s : 86400 - s + n;

            time = 12000 + Math.round(progress * 12000.0 / nightLength);
        }

        long day = Math.floorDiv(level.getDayTime(), 24000);
        level.setDayTime(day * 24000 + Math.floorMod(time, 24000));
    }

    private static ZonedDateTime sun(LocalDate date, boolean rise) {
        int day = date.getDayOfYear();
        double offset = date.atTime(12, 0).atZone(ZONE)
                .getOffset().getTotalSeconds() / 3600.0;

        double lngHour = LON / 15.0;
        double t = day + ((rise ? 6 : 18) - lngHour) / 24.0;

        double m = 0.9856 * t - 3.289;

        double l = norm(
                m
                        + 1.916 * Math.sin(Math.toRadians(m))
                        + 0.020 * Math.sin(Math.toRadians(2 * m))
                        + 282.634
        );

        double ra = Math.toDegrees(
                Math.atan(0.91764 * Math.tan(Math.toRadians(l)))
        );

        ra = norm(ra);
        ra += Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90;
        ra /= 15;

        double sinDec = 0.39782 * Math.sin(Math.toRadians(l));
        double cosDec = Math.cos(Math.asin(sinDec));

        double cosH = (
                Math.cos(Math.toRadians(90.833))
                        - sinDec * Math.sin(Math.toRadians(LAT))
        ) / (
                cosDec * Math.cos(Math.toRadians(LAT))
        );

        if (cosH > 1 || cosH < -1) return null;

        double h = Math.toDegrees(Math.acos(cosH));
        if (rise) h = 360 - h;
        h /= 15;

        double utc = h + ra - 0.06571 * t - 6.622 - lngHour;
        double local = normHours(utc + offset);

        int hour = (int) local;
        int minute = (int) ((local - hour) * 60);
        int second = (int) ((((local - hour) * 60) - minute) * 60);

        return date.atTime(hour, minute, second).atZone(ZONE);
    }

    private static void normalTime(ServerLevel level, ZonedDateTime now) {
        long time = Math.round(sec(now) * 24000.0 / 86400.0);
        long day = Math.floorDiv(level.getDayTime(), 24000);
        level.setDayTime(day * 24000 + time);
    }

    private static long sec(ZonedDateTime time) {
        return time.toLocalTime().toSecondOfDay();
    }

    private static double norm(double value) {
        value %= 360;
        return value < 0 ? value + 360 : value;
    }

    private static double normHours(double value) {
        value %= 24;
        return value < 0 ? value + 24 : value;
    }
}
