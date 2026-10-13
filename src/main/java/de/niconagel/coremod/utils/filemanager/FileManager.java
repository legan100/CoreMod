package de.niconagel.coremod.utils.filemanager;

import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    @Getter private final Path file;
    @Getter private static int numberOfFileManagers;

    public FileManager(String filePath) {
        file = Path.of(filePath);
        numberOfFileManagers++;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    public boolean createFile() {
        try {
            if (exists()) return false;
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.createFile(file);
            return true;
        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Erstellen: " + e.getMessage());
            return false;
        }
    }

    public boolean renameFile(String newName) {
        try {
            Path newPath = Path.of(newName);
            Path parent = newPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Umbenennen: " + e.getMessage());
            return false;
        }
    }

    public void writeInNextFreeLine(String text) {
        try {
            Files.writeString(file, text + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Schreiben: " + e.getMessage());
        }
    }

    public String readLine(int lineNumber) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lineNumber > 0 && lineNumber <= lines.size()
                    ? lines.get(lineNumber - 1) : null;
        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Lesen: " + e.getMessage());
            return null;
        }
    }

    public List<String> readAll() {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Lesen: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public String getParams(String line) {
        if (line == null) return null;
        int index = line.indexOf('=');
        return index == -1 ? null : line.substring(index + 1).trim();
    }

    public String getParams(int lineNumber) {
        return getParams(readLine(lineNumber));
    }

    public String getYamlValue(String key) {
        List<String> lines = readAll();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#"))
                continue;

            int index = line.indexOf(':');

            if (index == -1 ||
                    !line.substring(0, index).trim().equalsIgnoreCase(key))
                continue;

            String value = line.substring(index + 1).trim();

            if (value.equals("|") || value.equals(">"))
                return readYamlMultiline(lines, i, value.equals("|"));

            if (value.startsWith("-----BEGIN "))
                return readPemValue(lines, i, value);

            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'")))
                return value.substring(1, value.length() - 1);

            return value;
        }

        return null;
    }

    private String readYamlMultiline(
            List<String> lines,
            int startLine,
            boolean preserveLineBreaks) {

        StringBuilder result = new StringBuilder();
        int baseIndentation = -1;

        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.trim().isEmpty()) {
                if (preserveLineBreaks) result.append('\n');
                continue;
            }

            int indentation = getIndentation(line);

            if (indentation == 0) break;
            if (baseIndentation == -1) baseIndentation = indentation;
            if (indentation < baseIndentation) break;

            String content = line.substring(
                    Math.min(baseIndentation, line.length()));

            if (preserveLineBreaks) {
                if (!result.isEmpty()) result.append('\n');
                result.append(content);
            } else {
                if (!result.isEmpty()) result.append(' ');
                result.append(content);
            }
        }

        return result.toString().trim();
    }

    private String readPemValue(
            List<String> lines,
            int startLine,
            String firstValue) {

        StringBuilder result = new StringBuilder(firstValue.trim());

        if (firstValue.contains("-----END "))
            return result.toString();

        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#"))
                continue;

            result.append('\n').append(line);

            if (line.startsWith("-----END "))
                break;
        }

        return result.toString().trim();
    }

    private int getIndentation(String line) {
        int indentation = 0;

        while (indentation < line.length() &&
                (line.charAt(indentation) == ' ' ||
                        line.charAt(indentation) == '\t')) {
            indentation++;
        }

        return indentation;
    }

    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Löschen: " + e.getMessage());
        }
    }
}
