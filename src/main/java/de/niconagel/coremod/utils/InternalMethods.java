package de.niconagel.coremod.utils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TimeZone;

public class InternalMethods {

    private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Berlin");

    public static LocalTime getTime(){
        return LocalTime.now(TIME_ZONE).withNano(0);
    }
}
