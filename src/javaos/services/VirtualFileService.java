package javaos.services;

import javaos.models.FileItem;
import javaos.util.OSLogger;
import javaos.util.PathValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service managing sandboxed filesystem operations within the JavaOS virtual home directory.
 * Includes a dedicated Recycle Bin (trash) for safe deletion and restoration.
 */
public class VirtualFileService {

    private final PathValidator validator;
    private final Path trashRoot;

    public VirtualFileService() {
        this(Paths.get("data", "home"), Paths.get("data", "trash"));
    }

    public VirtualFileService(Path homePath, Path trashPath) {
        this.validator = new PathValidator(homePath);
        this.trashRoot = trashPath.toAbsolutePath().normalize();
        try {
            if (!Files.exists(trashRoot)) {
                Files.createDirectories(trashRoot);
            }
        } catch (IOException e) {
            OSLogger.error("Failed to initialize trash directory: " + e.getMessage());
        }
    }

    public PathValidator getValidator() {
        return validator;
    }

    public Path getHomeRoot() {
        return validator.getRootPath();
    }

    /**
     * Lists files in a virtual directory.
     *
     * @param virtualDir directory path relative to home, e.g. "/Documents" or ""
     * @return list of FileItem entries sorted: directories first, then alphabetical
     */
    public List<FileItem> listFiles(String virtualDir) throws IOException {
        Path target = validator.resolveSafe(virtualDir);
        if (!Files.exists(target)) {
            throw new NoSuchFileException("Directory does not exist: " + virtualDir);
        }
        if (!Files.isDirectory(target)) {
            throw new NotDirectoryException("Target is not a directory: " + virtualDir);
        }

        List<FileItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(target)) {
            for (Path p : stream.collect(Collectors.toList())) {
                boolean isDir = Files.isDirectory(p);
                long size = 0;
                try {
                    size = isDir ? 0 : Files.size(p);
                } catch (IOException ignored) {}
                long lastModified = 0;
                try {
                    lastModified = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException ignored) {}

                String vPath = validator.toVirtualPath(p);
                items.add(new FileItem(p.getFileName().toString(), vPath, isDir, size, lastModified));
            }
        }

        items.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return items;
    }

    public boolean createDirectory(String virtualPath) throws IOException {
        Path target = validator.resolveSafe(virtualPath);
        if (Files.exists(target)) {
            return false;
        }
        Files.createDirectories(target);
        OSLogger.info("Created virtual directory: " + virtualPath);
        return true;
    }

    public boolean createFile(String virtualPath, String initialContent) throws IOException {
        Path target = validator.resolveSafe(virtualPath);
        if (Files.exists(target)) {
            return false;
        }
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, initialContent == null ? "" : initialContent, StandardCharsets.UTF_8);
        OSLogger.info("Created virtual file: " + virtualPath);
        return true;
    }

    public String readFile(String virtualPath) throws IOException {
        Path target = validator.resolveSafe(virtualPath);
        if (!Files.exists(target)) {
            throw new NoSuchFileException("File does not exist: " + virtualPath);
        }
        if (Files.isDirectory(target)) {
            throw new IOException("Cannot read content of directory: " + virtualPath);
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    public void writeFile(String virtualPath, String content) throws IOException {
        Path target = validator.resolveSafe(virtualPath);
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        OSLogger.info("Wrote virtual file: " + virtualPath);
    }

    public void rename(String oldVirtualPath, String newName) throws IOException {
        Path source = validator.resolveSafe(oldVirtualPath);
        if (!Files.exists(source)) {
            throw new NoSuchFileException("Source file does not exist: " + oldVirtualPath);
        }
        if (source.equals(validator.getRootPath())) {
            throw new SecurityException("Cannot rename the virtual root directory");
        }
        Path target = source.getParent().resolve(newName).normalize();
        if (!target.startsWith(validator.getRootPath())) {
            throw new SecurityException("Target path escapes virtual root");
        }
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException("A file named '" + newName + "' already exists in this folder");
        }
        Files.move(source, target);
        OSLogger.info("Renamed " + oldVirtualPath + " to " + newName);
    }

    public void copy(String srcVirtual, String destVirtual) throws IOException {
        Path src = validator.resolveSafe(srcVirtual);
        Path dest = validator.resolveSafe(destVirtual);
        if (!Files.exists(src)) {
            throw new NoSuchFileException("Source does not exist: " + srcVirtual);
        }
        if (Files.isDirectory(dest)) {
            dest = dest.resolve(src.getFileName());
        }
        if (Files.isDirectory(src)) {
            copyDirectoryRecursively(src, dest);
        } else {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        OSLogger.info("Copied " + srcVirtual + " -> " + validator.toVirtualPath(dest));
    }

    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walk(source).forEach(p -> {
            try {
                Path dest = target.resolve(source.relativize(p));
                if (Files.isDirectory(p)) {
                    if (!Files.exists(dest)) Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void move(String srcVirtual, String destVirtual) throws IOException {
        Path src = validator.resolveSafe(srcVirtual);
        Path dest = validator.resolveSafe(destVirtual);
        if (!Files.exists(src)) {
            throw new NoSuchFileException("Source does not exist: " + srcVirtual);
        }
        if (src.equals(validator.getRootPath())) {
            throw new SecurityException("Cannot move virtual root directory");
        }
        if (Files.isDirectory(dest)) {
            dest = dest.resolve(src.getFileName());
        }
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        OSLogger.info("Moved " + srcVirtual + " -> " + validator.toVirtualPath(dest));
    }

    /**
     * Moves a file or folder into the Recycle Bin (trash).
     */
    public void moveToTrash(String virtualPath) throws IOException {
        Path target = validator.resolveSafe(virtualPath);
        if (!Files.exists(target)) {
            throw new NoSuchFileException("Target does not exist: " + virtualPath);
        }
        if (target.equals(validator.getRootPath())) {
            throw new SecurityException("Cannot delete root directory");
        }

        String fileName = target.getFileName().toString();
        String trashEntryName = System.currentTimeMillis() + "__" + fileName;
        Path trashItem = trashRoot.resolve(trashEntryName);

        // Store metadata file alongside to allow exact restoration
        String relativeOriginal = validator.toVirtualPath(target);
        Path metaFile = trashRoot.resolve(trashEntryName + ".meta");
        Files.writeString(metaFile, relativeOriginal, StandardCharsets.UTF_8);

        Files.move(target, trashItem, StandardCopyOption.REPLACE_EXISTING);
        OSLogger.info("Moved " + virtualPath + " to Recycle Bin as " + trashEntryName);
    }

    /**
     * Lists items currently in the Recycle Bin.
     */
    public List<TrashItem> listTrash() throws IOException {
        List<TrashItem> items = new ArrayList<>();
        if (!Files.exists(trashRoot)) return items;

        try (Stream<Path> stream = Files.list(trashRoot)) {
            for (Path p : stream.collect(Collectors.toList())) {
                if (p.toString().endsWith(".meta")) continue;

                String entryName = p.getFileName().toString();
                Path meta = trashRoot.resolve(entryName + ".meta");
                String originalPath = "/" + entryName;
                if (Files.exists(meta)) {
                    try {
                        originalPath = Files.readString(meta, StandardCharsets.UTF_8).trim();
                    } catch (Exception ignored) {}
                }

                String display = entryName;
                if (entryName.contains("__")) {
                    display = entryName.substring(entryName.indexOf("__") + 2);
                }

                items.add(new TrashItem(entryName, display, originalPath, Files.isDirectory(p), Files.getLastModifiedTime(p).toMillis()));
            }
        }
        return items;
    }

    /**
     * Restores an item from the Recycle Bin back to its original virtual home path.
     */
    public boolean restoreFromTrash(String entryName) throws IOException {
        Path trashItem = trashRoot.resolve(entryName);
        Path metaFile = trashRoot.resolve(entryName + ".meta");
        if (!Files.exists(trashItem)) {
            return false;
        }

        String originalPath = "/" + entryName;
        if (Files.exists(metaFile)) {
            originalPath = Files.readString(metaFile, StandardCharsets.UTF_8).trim();
        }

        Path destination = validator.resolveSafe(originalPath);
        if (destination.getParent() != null && !Files.exists(destination.getParent())) {
            Files.createDirectories(destination.getParent());
        }

        Files.move(trashItem, destination, StandardCopyOption.REPLACE_EXISTING);
        if (Files.exists(metaFile)) {
            Files.deleteIfExists(metaFile);
        }
        OSLogger.info("Restored " + entryName + " back to " + originalPath);
        return true;
    }

    /**
     * Empties the Recycle Bin permanently.
     */
    public void emptyTrash() throws IOException {
        if (!Files.exists(trashRoot)) return;
        try (Stream<Path> stream = Files.walk(trashRoot)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(trashRoot))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            OSLogger.warn("Failed to delete trash item " + p + ": " + e.getMessage());
                        }
                    });
        }
        OSLogger.info("Recycle Bin emptied permanently.");
    }

    /**
     * Searches for files by name matching substring query recursively within virtual home.
     */
    public List<FileItem> search(String query) throws IOException {
        final List<FileItem> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            // Empty query: walk and return all files
            try (Stream<Path> stream = Files.walk(validator.getRootPath())) {
                stream.filter(p -> !p.equals(validator.getRootPath()) && !Files.isDirectory(p))
                        .forEach(p -> {
                            long size = 0;
                            try { size = Files.size(p); } catch (IOException ignored) {}
                            long mod = 0;
                            try { mod = Files.getLastModifiedTime(p).toMillis(); } catch (IOException ignored) {}
                            results.add(new FileItem(p.getFileName().toString(), validator.toVirtualPath(p), false, size, mod));
                        });
            }
            return results;
        }

        String q = query.toLowerCase().trim();
        try (Stream<Path> stream = Files.walk(validator.getRootPath())) {
            stream.filter(p -> !p.equals(validator.getRootPath()))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(q))
                    .forEach(p -> {
                        boolean isDir = Files.isDirectory(p);
                        long size = 0;
                        try {
                            size = isDir ? 0 : Files.size(p);
                        } catch (IOException ignored) {}
                        long mod = 0;
                        try {
                            mod = Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException ignored) {}

                        results.add(new FileItem(p.getFileName().toString(), validator.toVirtualPath(p), isDir, size, mod));
                    });
        }
        return results;
    }

    public static class TrashItem {
        public final String entryId;
        public final String originalName;
        public final String originalVirtualPath;
        public final boolean isDirectory;
        public final long deletedTimeMillis;

        public TrashItem(String entryId, String originalName, String originalVirtualPath, boolean isDirectory, long deletedTimeMillis) {
            this.entryId = entryId;
            this.originalName = originalName;
            this.originalVirtualPath = originalVirtualPath;
            this.isDirectory = isDirectory;
            this.deletedTimeMillis = deletedTimeMillis;
        }
    }
}
