package javaos.apps;

import javaos.core.JavaOS;
import javaos.ui.InternalWindow;

import javax.swing.*;
import java.awt.Dimension;

/**
 * Abstract base class for all JavaOS desktop applications.
 * Implements common lifecycle hooks, window management links, and context access.
 */
public abstract class OSApplication {

    private final AppDescriptor descriptor;
    private InternalWindow window;
    private JavaOS os;

    public OSApplication(AppDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public void init(JavaOS os) {
        this.os = os;
    }

    public JavaOS getOS() {
        return os;
    }

    public AppDescriptor getDescriptor() {
        return descriptor;
    }

    public InternalWindow getWindow() {
        return window;
    }

    public void setWindow(InternalWindow window) {
        this.window = window;
    }

    /**
     * Constructs and returns the graphical content pane for this application.
     */
    public abstract JComponent createContent();

    /**
     * Preferred initial size of the application window.
     */
    public Dimension getPreferredWindowSize() {
        return new Dimension(720, 480);
    }

    /**
     * Lifecycle callback invoked when the application window is created and shown.
     */
    public void onOpen() {
        // Optional override
    }

    /**
     * Verifies if the application can safely close (e.g. unsaved changes prompts).
     * @return true to permit close, false to cancel
     */
    public boolean canClose() {
        return true;
    }

    /**
     * Lifecycle callback invoked right before the application is disposed.
     */
    public void onClose() {
        // Optional override
    }
}
