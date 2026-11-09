package javaos.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sandboxing and path validation utility.
 * Guarantees that all virtual file operations are strictly confined within the JavaOS home directory.
 */
public final class PathValidator {

    private final Path rootPath;

    public PathValidator(Path rootPath) {
        if (rootPath == null) {
            throw new IllegalArgumentException("Root path cannot be null");
        }
        try {
            this.rootPath = rootPath.toAbsolutePath().normalize();
            if (!Files.exists(this.rootPath)) {
                Files.createDirectories(this.rootPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize virtual root directory: " + rootPath, e);
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Resolves a virtual path relative to the virtual home root, ensuring it does not escape.
     *
     * @param virtualPath path string such as "Documents/notes.txt" or "/Projects/test.txt"
     * @return normalized absolute Path on disk safely within rootPath
     * @throws SecurityException if the resolved path escapes the sandbox
     */
    public Path resolveSafe(String virtualPath) throws SecurityException {
        if (virtualPath == null || virtualPath.trim().isEmpty()) {
            return rootPath;
        }

        String cleaned = virtualPath.trim().replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }

        Path candidate = rootPath.resolve(cleaned).normalize();

        // Enforce boundary check
        if (!candidate.startsWith(rootPath)) {
            OSLogger.warn("Path traversal violation attempted: " + virtualPath);
            throw new SecurityException("Access Denied: Path escapes JavaOS virtual home sandbox (" + virtualPath + ")");
        }

        // Handle symlink verification if target exists
        if (Files.exists(candidate)) {
            try {
                Path realPath = candidate.toRealPath();
                if (!realPath.startsWith(rootPath)) {
                    OSLogger.warn("Symlink traversal violation detected: " + virtualPath + " -> " + realPath);
                    throw new SecurityException("Access Denied: Symlink target escapes JavaOS sandbox");
                }
            } catch (IOException e) {
                // If real path cannot be resolved due to permissions or broken link
                OSLogger.warn("Could not resolve real path for " + candidate + ": " + e.getMessage());
            }
        }

        return candidate;
    }

    /**
     * Converts a safe absolute disk path back into a clean virtual path (e.g. "/Documents/welcome.txt").
     */
    public String toVirtualPath(Path absolutePath) {
        if (absolutePath == null) {
            return "/";
        }
        Path normalized = absolutePath.toAbsolutePath().normalize();
        if (normalized.equals(rootPath)) {
            return "/";
        }
        if (normalized.startsWith(rootPath)) {
            Path relative = rootPath.relativize(normalized);
            return "/" + relative.toString().replace('\\', '/');
        }
        return "/" + normalized.getFileName().toString();
    }
}
