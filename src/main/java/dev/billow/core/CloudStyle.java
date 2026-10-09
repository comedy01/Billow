package dev.billow.core;

import java.util.Locale;

public enum CloudStyle implements Choice {
    VANILLA,
    ROUNDED,
    REALISTIC,
    SHAPES;

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean usesCells() {
        return this == ROUNDED || this == SHAPES;
    }

    public static CloudStyle byId(String id) {
        for (CloudStyle style : values()) {
            if (style.id().equalsIgnoreCase(id)) {
                return style;
            }
        }
        return ROUNDED;
    }
}
