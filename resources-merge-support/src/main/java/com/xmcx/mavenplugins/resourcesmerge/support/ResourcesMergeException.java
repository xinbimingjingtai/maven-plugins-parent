package com.xmcx.mavenplugins.resourcesmerge.support;

/**
 * Exception when merging resources.
 *
 * @author xmcx
 * @since 2024.05.30
 */
public class ResourcesMergeException extends RuntimeException {

    public ResourcesMergeException(String message) {
        super(message);
    }

    public ResourcesMergeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourcesMergeException(Throwable cause) {
        super(cause);
    }
}
