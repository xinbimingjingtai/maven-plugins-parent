package com.xmcx.mavenplugins.resourcesmerge.support;

import java.io.File;
import java.util.Comparator;

/**
 * Default implementation of {@link ResourcesComparator}, and compared by {@link File#getName()}.
 *
 * @author xmcx
 * @since 2024.05.30
 */
public class DefaultResourcesComparator implements ResourcesComparator {

    /**
     * Compares its two resources for order by {@link File#getName()}.
     */
    @Override
    public int compare(File a, File b) {
        return Comparator.comparing(File::getName).compare(a, b);
    }

}
