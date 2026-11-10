package com.vku.pulseoj.common.enums;

import java.io.Serializable;

/**
 * Programming languages supported by the PulseOJ Sandbox.
 */
public enum Language implements Serializable {
    JAVA("Java 17+", ".java", "Main"),
    CPP("C++ 17 (GCC)", ".cpp", "solution"),
    PYTHON("Python 3", ".py", "solution");

    private final String displayName;
    private final String extension;
    private final String defaultFileName;

    Language(String displayName, String extension, String defaultFileName) {
        this.displayName = displayName;
        this.extension = extension;
        this.defaultFileName = defaultFileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    public String getDefaultFileName() {
        return defaultFileName;
    }

    public String getFullSourceFileName() {
        return defaultFileName + extension;
    }
}
