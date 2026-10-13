package de.niconagel.coremod.utils;

import java.util.List;

/**
 * Pixelbreiten in der Minecraft-Standardschrift (gemessen aus ascii.png, inkl. 1 px Abstand),
 * um Text in der Tablist bündig auszurichten. Clients mit "Unicode-Schrift erzwingen" sehen es leicht verschoben.
 */
public final class TextWidth {

    private TextWidth() {
    }

    public static int of(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            width += switch (c) {
                case 'i', '|', '!', '.', ',', ':', ';', '\'' -> 2;
                case 'l', '`' -> 3;
                case 't', 'I', ' ', '[', ']', '(', ')', '{', '}', '*', '"' -> 4;
                case 'f', 'k', '<', '>' -> 5;
                default -> 6;
            };
        }
        return width;
    }

    /**
     * Leerzeichen sind 4 px breit, fette 5 px. Damit lässt sich jede Breite ab 12 px exakt treffen.
     *
     * @return {normale, fette} Leerzeichen für genau {@code px} Pixel, oder null wenn unmöglich
     */
    public static int[] padding(int px) {
        for (int bold = 0; bold <= 3; bold++) {
            int rest = px - 5 * bold;
            if (rest >= 0 && rest % 4 == 0) {
                return new int[]{rest / 4, bold};
            }
        }
        return null;
    }

    /** Kleinste Breite, auf die sich alle Texte exakt auffüllen lassen. */
    public static int commonWidth(List<String> texts) {
        int target = texts.stream().mapToInt(TextWidth::of).max().orElse(0);
        while (!fitsAll(texts, target)) {
            target++;
        }
        return target;
    }

    private static boolean fitsAll(List<String> texts, int target) {
        return texts.stream().allMatch(text -> padding(target - of(text)) != null);
    }
}
