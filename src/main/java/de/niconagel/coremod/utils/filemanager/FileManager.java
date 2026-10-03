package de.niconagel.coremod.utils.filemanager;

import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    @Getter
    private final Path file;

    @Getter
    private static int numberOfFileManagers = 0;

    public FileManager(String filePath) {
        this.file = Path.of(filePath);
        ++numberOfFileManagers;
    }

    public boolean exists() {
        return Files.exists(this.file);
    }

    public boolean createFile() {
        try {
            if (!this.exists()) {
                Path parent = this.file.getParent();

                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                Files.createFile(this.file);
                return true;
            }

            return false;

        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Erstellen der Datei: " + e.getMessage());
            return false;
        }
    }

    public boolean renameFile(String newName) {
        try {
            Path newPath = Path.of(newName);
            Path parent = newPath.getParent();

            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.move(this.file, newPath, StandardCopyOption.REPLACE_EXISTING);
            return true;

        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Umbenennen der Datei: " + e.getMessage());
            return false;
        }
    }

    public void writeInNextFreeLine(String text) {
        try {
            Files.writeString(
                    this.file,
                    text + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Schreiben: " + e.getMessage());
        }
    }

    public String readLine(int lineNumber) {
        try {
            List<String> lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);

            if (lineNumber >= 1 && lineNumber <= lines.size()) {
                return lines.get(lineNumber - 1);
            }

            return null;

        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Lesen der Zeile: " + e.getMessage());
            return null;
        }
    }

    public List<String> readAll() {
        try {
            return Files.readAllLines(this.file, StandardCharsets.UTF_8);

        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Lesen: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public String getParams(String line) {
        if (line == null) return null;

        int index = line.indexOf('=');
        if (index == -1) return null;

        return line.substring(index + 1).trim();
    }

    public String getParams(int lineNumber) {
        String line = this.readLine(lineNumber);
        if (line == null) return null;

        int index = line.indexOf('=');
        if (index == -1) return null;

        return line.substring(index + 1).trim();
    }

    public String getYamlValue(String key) {
        List<String> lines = readAll();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            int index = line.indexOf(':');

            if (index == -1) {
                continue;
            }

            String currentKey = line.substring(0, index).trim();

            if (!currentKey.equalsIgnoreCase(key)) {
                continue;
            }

            String value = line.substring(index + 1).trim();

            if (value.equals("|") || value.equals(">")) {
                StringBuilder result = new StringBuilder();

                for (int j = i + 1; j < lines.size(); j++) {
                    String nextLine = lines.get(j);

                    if (nextLine.trim().isEmpty()) {
                        if (value.equals("|")) {
                            result.append('\n');
                        }
                        continue;
                    }

                    int indentation = 0;

                    while (indentation < nextLine.length()
                            && Character.isWhitespace(nextLine.charAt(indentation))) {
                        indentation++;
                    }

                    if (indentation == 0) {
                        break;
                    }

                    String content = nextLine.substring(indentation);

                    if (value.equals("|")) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }

                        result.append(content);
                    } else {
                        if (result.length() > 0) {
                            result.append(' ');
                        }

                        result.append(content);
                    }
                }

                return result.toString();
            }

            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }

            return value;
        }

        return null;
    }

    public void delete() {
        try {
            Files.deleteIfExists(this.file);

        } catch (IOException e) {
            System.err.println("[FileManager] Fehler beim Löschen: " + e.getMessage());
        }
    }
}
