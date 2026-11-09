package javaos.core;

import javaos.util.OSLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks background tasks, workers, and process handles inside JavaOS.
 */
public class ProcessRegistry {

    public static class TaskInfo {
        public final String id;
        public final String name;
        public final long startTime;
        public final Thread thread;

        public TaskInfo(String id, String name, Thread thread) {
            this.id = id;
            this.name = name;
            this.startTime = System.currentTimeMillis();
            this.thread = thread;
        }
    }

    private final Map<String, TaskInfo> activeTasks = new ConcurrentHashMap<>();

    public void registerTask(String id, String name, Thread thread) {
        activeTasks.put(id, new TaskInfo(id, name, thread));
        OSLogger.debug("Registered background task: " + name + " (ID: " + id + ")");
    }

    public void unregisterTask(String id) {
        activeTasks.remove(id);
    }

    public List<TaskInfo> getActiveTasks() {
        return new ArrayList<>(activeTasks.values());
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }
}
