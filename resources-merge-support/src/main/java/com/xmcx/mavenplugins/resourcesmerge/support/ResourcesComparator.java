package com.xmcx.mavenplugins.resourcesmerge.support;

import java.io.File;

/**
 * Resources file comparator. This is useful in situations where there are sequential requirements for resources.
 *
 * @author xmcx
 * @since 2024.05.30
 */
public interface ResourcesComparator {

    /**
     * Compares its two resources for order.
     */
    int compare(File a, File b);

}
