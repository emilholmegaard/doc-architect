package com.docarchitect.core.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for file operations.
 */
public final class FileUtils {

    private FileUtils() {
        // Utility class
    }

    /**
     * Finds files matching a glob pattern starting from a root directory.
     *
     * <p>Example patterns: pom.xml files, Java files, Kotlin files in src/main.
     *
     * @param rootPath root directory to search from
     * @param globPattern glob pattern
     * @return list of matching paths
     * @throws IOException if directory traversal fails
     */
    public static List<Path> findFiles(Path rootPath, String globPattern) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

        try (Stream<Path> paths = Files.walk(rootPath)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    Path relativePath = rootPath.relativize(path);
                    return matcher.matches(relativePath);
                })
                .toList();
        }
    }

    /**
     * Finds files matching a glob pattern in multiple root directories.
     *
     * @param rootPaths root directories to search from
     * @param globPattern glob pattern
     * @return list of matching paths
     * @throws IOException if directory traversal fails
     */
    public static List<Path> findFiles(List<Path> rootPaths, String globPattern) throws IOException {
        return rootPaths.stream()
            .flatMap(rootPath -> {
                try {
                    return findFiles(rootPath, globPattern).stream();
                } catch (IOException e) {
                    return Stream.empty();
                }
            })
            .toList();
    }

    /**
     * Reads a file as a string.
     *
     * @param path path to file
     * @return file content as string
     * @throws IOException if reading fails
     */
    public static String readString(Path path) throws IOException {
        return Files.readString(path);
    }

    /**
     * Reads all lines from a file.
     *
     * @param path path to file
     * @return list of lines
     * @throws IOException if reading fails
     */
    public static List<String> readLines(Path path) throws IOException {
        return Files.readAllLines(path);
    }

    /**
     * Checks if a file exists.
     *
     * @param path path to check
     * @return true if file exists
     */
    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    /**
     * Checks if a path is a directory.
     *
     * @param path path to check
     * @return true if path is a directory
     */
    public static boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    /**
     * Gets the file extension.
     *
     * @param path file path
     * @return file extension without dot, or empty string if no extension
     */
    public static String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
}
