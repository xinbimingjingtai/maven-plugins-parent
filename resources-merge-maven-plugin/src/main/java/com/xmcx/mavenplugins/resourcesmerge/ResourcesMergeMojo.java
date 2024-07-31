package com.xmcx.mavenplugins.resourcesmerge;

import com.xmcx.mavenplugins.resourcesmerge.support.DefaultResourcesMergeStrategy;
import com.xmcx.mavenplugins.resourcesmerge.support.ResourcesMergeException;
import com.xmcx.mavenplugins.resourcesmerge.support.ResourcesMergeStrategy;
import lombok.Getter;
import lombok.Setter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Goal which touches a timestamp file.
 *
 * @author xmcx
 * @since 2024.05.30
 */
@Getter
@Setter
@Mojo(name = "resources-merge", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class ResourcesMergeMojo extends AbstractMojo {

    /**
     * MavenProject
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * MavenSession
     */
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    /**
     * MojoExecution
     */
    @Parameter(defaultValue = "${mojoExecution}", required = true, readonly = true)
    private MojoExecution mojoExecution;

    /**
     * Default resources merge strategies.
     *
     * @see DefaultResourcesMergeStrategy
     */
    @Parameter
    private List<DefaultResourcesMergeStrategy> defaultStrategies;

    /**
     * Custom resources merge strategies.
     * Use attribute {@code implementation} define implementation classes for other strategies.
     */
    @Parameter
    private List<ResourcesMergeStrategy> customStrategies;

    /**
     * Skip the execution of the plugin if you need to.
     */
    @Parameter(property = "merge.properties.skip", defaultValue = "false")
    private boolean skip;

    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping the execution.");
            return;
        }
        List<ResourcesMergeStrategy> strategies = new ArrayList<>();
        if (defaultStrategies != null) {
            strategies.addAll(defaultStrategies);
        }
        if (customStrategies != null) {
            strategies.addAll(customStrategies);
        }
        if (strategies.isEmpty()) {
            throw new MojoExecutionException("Require at least one default or custom strategy");
        }
        for (ResourcesMergeStrategy strategy : strategies) {
            try {
                strategy.merge(project, session, mojoExecution);
            } catch (ResourcesMergeException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

}
