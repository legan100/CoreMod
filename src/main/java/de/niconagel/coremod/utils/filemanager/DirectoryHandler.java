package de.niconagel.coremod.utils.filemanager;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public class DirectoryHandler {

    private final String userPath =
            System.getProperty("user.home");

    private final String rootPath =
            Path.of(userPath).getRoot().toString();

    private final String programPath =
            System.getProperty("user.dir");

    @Setter
    private String path;

    public DirectoryHandler() {
        this.path = "";
    }

    public DirectoryHandler(String path) {
        this.path = path;
    }

    public String generateDirectory() {
        return generateDirectory(path);
    }

    public String generateDirectory(String directoryPath) {

        Path dir = Path.of(directoryPath);

        if (Files.exists(dir)) {
            path = dir.toAbsolutePath().toString();
            return "Directory already exists.";
        }

        try {
            Files.createDirectories(dir);

            path = dir.toAbsolutePath().toString();

            return "Directory " + path + " created.";

        } catch (IOException e) {

            return "Failed to create directory: "
                    + e.getMessage();
        }
    }

    public boolean existsDirectory(String directoryPath) {
        return Files.exists(Path.of(directoryPath));
    }

    public String existDirectory(String directoryPath) {
        return existsDirectory(directoryPath)
                ? "Directory exists."
                : "Directory does not exist.";
    }

    public String getAbsolutePath() {
        return Path.of(path)
                .toAbsolutePath()
                .toString();
    }

    public String getRelativePath() {

        try {

            return Path.of(programPath)
                    .relativize(
                            Path.of(path)
                                    .toAbsolutePath()
                    )
                    .toString();

        } catch (Exception e) {

            return Path.of(path)
                    .getFileName()
                    .toString();
        }
    }

    public int getLength() {
        return path.length();
    }

    public String getLowerCase() {
        return path.toLowerCase();
    }

    public String getUpperCase() {
        return path.toUpperCase();
    }
}