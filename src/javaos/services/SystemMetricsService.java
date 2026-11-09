package javaos.services;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Collects accurate real-time telemetry from the JVM and host operating system.
 * Explicitly separates JVM metrics from Host System metrics.
 */
public class SystemMetricsService {

    private final long startupTimeMillis;
    private final Runtime runtime;
    private final RuntimeMXBean runtimeMXBean;
    private final ThreadMXBean threadMXBean;
    private final OperatingSystemMXBean osMXBean;

    public SystemMetricsService() {
        this.startupTimeMillis = System.currentTimeMillis();
        this.runtime = Runtime.getRuntime();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
    }

    public long getJavaOSUptimeMillis() {
        return System.currentTimeMillis() - startupTimeMillis;
    }

    public String getFormattedUptime() {
        Duration d = Duration.ofMillis(getJavaOSUptimeMillis());
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    // --- JVM Heap Metrics ---

    public long getJvmUsedMemory() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long getJvmTotalMemory() {
        return runtime.totalMemory();
    }

    public long getJvmMaxMemory() {
        return runtime.maxMemory();
    }

    public double getJvmHeapUsagePercent() {
        long max = getJvmMaxMemory();
        if (max == Long.MAX_VALUE || max <= 0) {
            max = getJvmTotalMemory();
        }
        return (double) getJvmUsedMemory() / max * 100.0;
    }

    public int getActiveThreadCount() {
        return threadMXBean.getThreadCount();
    }

    public int getAvailableProcessors() {
        return runtime.availableProcessors();
    }

    public String getJavaVersion() {
        return System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
    }

    public String getHostOsName() {
        return System.getProperty("os.name") + " " + System.getProperty("os.arch");
    }

    // --- Host System Metrics via supported MXBean APIs ---

    public long getHostTotalPhysicalMemory() {
        return invokeLongMethod(osMXBean, "getTotalMemorySize", "getTotalPhysicalMemorySize");
    }

    public long getHostFreePhysicalMemory() {
        return invokeLongMethod(osMXBean, "getFreeMemorySize", "getFreePhysicalMemorySize");
    }

    public double getHostRamUsagePercent() {
        long total = getHostTotalPhysicalMemory();
        long free = getHostFreePhysicalMemory();
        if (total > 0 && free >= 0) {
            return ((double) (total - free) / total) * 100.0;
        }
        return getJvmHeapUsagePercent();
    }

    public double getHostCpuLoad() {
        Double load = invokeDoubleMethod(osMXBean, "getCpuLoad", "getSystemCpuLoad");
        if (load != null && load >= 0.0) {
            return load * 100.0;
        }
        return -1.0; // Metric unavailable
    }

    public double getProcessCpuLoad() {
        Double load = invokeDoubleMethod(osMXBean, "getProcessCpuLoad");
        if (load != null && load >= 0.0) {
            return load * 100.0;
        }
        return -1.0;
    }

    private long invokeLongMethod(Object target, String... methodNames) {
        if (target == null) return -1;
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                Object val = m.invoke(target);
                if (val instanceof Number) {
                    return ((Number) val).longValue();
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private Double invokeDoubleMethod(Object target, String... methodNames) {
        if (target == null) return null;
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                Object val = m.invoke(target);
                if (val instanceof Number) {
                    return ((Number) val).doubleValue();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
