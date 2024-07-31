// package com.xmcx.mavenplugins.resourcesmerge.support;
//
// import java.io.File;
// import java.util.Comparator;
//
// /**
//  * Resources file comparator.
//  *
//  * @author xmcx
//  * @since 2024.05.30
//  */
// public interface ResourcesComparator {
//
//     /**
//      * Compares its two resources for order. Using {@link File#getName()} compare by default.
//      * <p>
//      * Comparison of {@code null} resource file is unimportant.
//      */
//     default int compare(File a, File b) {
//         return Comparator.nullsLast(Comparator.comparing(File::getName)).compare(a, b);
//     }
//
// }
