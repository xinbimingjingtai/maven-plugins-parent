package com.xmcx.mavenplugins.resourcesmerge.support;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

/**
 * The strategy of resources merge.
 *
 * @author xmcx
 * @since 2024.05.30
 */
public interface ResourcesMergeStrategy {

    /**
     * Merge resources.
     */
    void merge(MavenProject project, MavenSession session, MojoExecution mojoExecution) throws ResourcesMergeException;

}
