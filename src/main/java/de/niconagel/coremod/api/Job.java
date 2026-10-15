package de.niconagel.coremod.api;

import lombok.Getter;

import java.util.Locale;

/**
 * Was ein Spieler gerade spielt, gewählt im Hauptmenü. Die Rechte im Spiel kommen vom Job (jobs.json),
 * welche Jobs ein Spieler wählen darf, von seiner Rolle über "job.&lt;name&gt;" (permissions.json).
 */
public enum Job {
    ZUSCHAUER("Zuschauer", "Die Welt ansehen, auch im Spectator-Modus."),
    BUILDER("Builder", "Bauen, Fliegen und Teleportieren."),
    TF("Triebfahrzeugführer", "Züge fahren."),
    FDL("Fahrdienstleiter", "Stellwerk bedienen."),
    TEAM("Team", "Alle Rechte.");

    /** So heißt der Job im Menü und in der jobs.json. */
    @Getter
    private final String displayName;

    @Getter
    private final String description;

    Job(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** Recht der Rolle, diesen Job zu wählen: "job.zuschauer", "job.builder", … */
    public String getPermission() {
        return "job." + name().toLowerCase(Locale.ROOT);
    }

    /** @return der Job zum Namen aus der jobs.json ("Builder" oder "BUILDER") oder null */
    public static Job byName(String name) {
        for (Job job : values()) {
            if (job.displayName.equalsIgnoreCase(name) || job.name().equalsIgnoreCase(name)) {
                return job;
            }
        }
        return null;
    }
}
