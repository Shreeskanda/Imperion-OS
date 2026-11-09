package javaos.core;

import javaos.apps.AppDescriptor;
import javaos.apps.OSApplication;
import javaos.ui.InternalWindow;
import javaos.util.OSLogger;

import javax.swing.*;
import java.awt.Dimension;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Registry and lifecycle manager for all built-in JavaOS applications.
 */
public class ApplicationManager {

    private final JavaOS os;
    private final Map<String, AppDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, Supplier<OSApplication>> factories = new LinkedHashMap<>();
    private final List<OSApplication> runningApps = new CopyOnWriteArrayList<>();
    private final List<Runnable> runningAppsListeners = new CopyOnWriteArrayList<>();

    public ApplicationManager(JavaOS os) {
        this.os = os;
    }

    public synchronized void registerApp(AppDescriptor descriptor, Supplier<OSApplication> factory) {
        descriptors.put(descriptor.getId(), descriptor);
        factories.put(descriptor.getId(), factory);
        OSLogger.debug("Registered application: " + descriptor.getName() + " [" + descriptor.getId() + "]");
    }

    public List<AppDescriptor> getRegisteredApps() {
        return new ArrayList<>(descriptors.values());
    }

    public AppDescriptor getDescriptor(String id) {
        return descriptors.get(id);
    }

    public List<OSApplication> getRunningApps() {
        return Collections.unmodifiableList(new ArrayList<>(runningApps));
    }

    public synchronized OSApplication launchApp(String appId) {
        AppDescriptor desc = descriptors.get(appId);
        if (desc == null) {
            OSLogger.error("Cannot launch unknown application: " + appId);
            return null;
        }

        // Single instance check
        if (desc.isSingleInstance()) {
            for (OSApplication running : runningApps) {
                if (running.getDescriptor().getId().equals(appId)) {
                    if (running.getWindow() != null) {
                        if (running.getWindow().isMinimized()) {
                            running.getWindow().restore();
                        }
                        os.getWindowManager().setActiveWindow(running.getWindow());
                    }
                    return running;
                }
            }
        }

        Supplier<OSApplication> factory = factories.get(appId);
        if (factory == null) {
            OSLogger.error("No factory found for application: " + appId);
            return null;
        }

        OSApplication app = factory.get();
        app.init(os);

        JComponent content = app.createContent();
        String windowId = appId + "-" + System.currentTimeMillis();
        InternalWindow window = new InternalWindow(windowId, desc.getName(), desc.getIcon());
        window.getContentPane().add(content);
        app.setWindow(window);

        window.setStateListener(new InternalWindow.WindowStateListener() {
            @Override
            public void onClosed(InternalWindow w) {
                closeApp(app);
            }

            @Override
            public void onMinimized(InternalWindow w) {
                notifyRunningAppsChanged();
            }

            @Override
            public void onRestored(InternalWindow w) {
                notifyRunningAppsChanged();
            }

            @Override
            public void onFocused(InternalWindow w) {
                os.getWindowManager().setActiveWindow(w);
                notifyRunningAppsChanged();
            }
        });

        runningApps.add(app);
        Dimension preferredSize = app.getPreferredWindowSize();
        os.getWindowManager().addWindow(window, preferredSize.width, preferredSize.height);

        app.onOpen();
        notifyRunningAppsChanged();
        OSLogger.info("Launched application: " + desc.getName() + " (instance: " + windowId + ")");

        return app;
    }

    public synchronized boolean closeApp(OSApplication app) {
        if (!app.canClose()) {
            return false;
        }

        try {
            app.onClose();
        } catch (Exception e) {
            OSLogger.error("Error during application onClose: " + e.getMessage());
        }

        runningApps.remove(app);
        if (app.getWindow() != null) {
            os.getWindowManager().removeWindow(app.getWindow());
        }

        notifyRunningAppsChanged();
        OSLogger.info("Closed application: " + app.getDescriptor().getName());
        return true;
    }

    public void addRunningAppsListener(Runnable r) {
        runningAppsListeners.add(r);
    }

    public void removeRunningAppsListener(Runnable r) {
        runningAppsListeners.remove(r);
    }

    private void notifyRunningAppsChanged() {
        for (Runnable r : runningAppsListeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }
}
