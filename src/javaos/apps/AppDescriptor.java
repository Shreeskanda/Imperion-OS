package javaos.apps;

import javax.swing.Icon;

/**
 * Metadata descriptor for applications registered within JavaOS.
 */
public class AppDescriptor {

    public enum Category {
        UTILITIES, SYSTEM, PRODUCTIVITY, AI, ACCESSORIES, MEDIA, GAMES
    }

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final Icon icon;
    private final boolean singleInstance;

    public AppDescriptor(String id, String name, String description, Category category, Icon icon, boolean singleInstance) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.singleInstance = singleInstance;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Icon getIcon() {
        return icon;
    }

    public boolean isSingleInstance() {
        return singleInstance;
    }

    @Override
    public String toString() {
        return name;
    }
}
