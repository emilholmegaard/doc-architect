package com.docarchitect.core.model;

/**
 * Severity level for quality gaps detected during scanning.
 *
 * <p>Used to indicate the importance of gaps in scan coverage or quality.</p>
 *
 * @since 1.0.0
 */
public enum GapSeverity {
    /**
     * Informational - no action required, just for awareness.
     */
    INFO,

    /**
     * Warning - potential issue that should be reviewed.
     */
    WARNING,

    /**
     * Error - significant issue that may affect scan quality.
     */
    ERROR
}
