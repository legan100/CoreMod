package de.niconagel.coremod.api;

import lombok.Getter;

import java.util.Locale;

/**
 * Die Reihenfolge ist das Gewicht: weiter unten = wichtiger (Chef steht in der Tablist ganz oben).
 */
public enum Group {
    GAST("Gast", '7'),
    TF_AZUBI("TF-Azubi", 'a'),
    TF("TF", '2'),
    FDL_AZUBI("FDL-Azubi", '9'),
    FDL("FDL", '3'),
    BUILDER("Builder", '6'),
    DEVELOPER("Developer", "Dev", 'b'),
    CHEF("Chef", 'c');

    /** Offizieller Name: so heißt die Rolle in der API und in der permissions.json. */
    @Getter
    private final String displayName;

    /** Kurzname für Tablist, Chat und /group. */
    @Getter
    private final String prefix;

    /** Minecraft-Farbcode, wie bei "&c" bzw. "§c". */
    @Getter
    private final char colorCode;

    private final String key;

    Group(String displayName, char colorCode) {
        this(displayName, displayName, colorCode);
    }

    Group(String displayName, String prefix, char colorCode) {
        this.displayName = displayName;
        this.prefix = prefix;
        this.colorCode = colorCode;
        this.key = normalize(displayName);
    }

    /** Höher = wichtiger. Gast = 0, Chef = 7. */
    public int getWeight() {
        return ordinal();
    }

    /**
     * Findet die Gruppe unabhängig von Schreibweise: "TF-Azubi", "tf_azubi", "TFAzubi" → TF_AZUBI.
     *
     * @return die Gruppe oder null, wenn es keine gibt
     */
    public static Group byName(String name) {
        String key = normalize(name);
        for (Group group : values()) {
            if (group.key.equals(key)) {
                return group;
            }
        }
        return null;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
