package com.xmcx.mavenplugins.resourcesmerge.support;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of {@link ResourcesMergeStrategy}.
 * <p>
 * The origin resources is under the child directory {@link #defaultOriginDir} of {@link #buildDirExpression},
 * that's means that if you want to use this implementation,
 * you should add this plugin dependency after the plugin {@code maven-resources-plugin}.
 * <p>
 * If the {@link #mergeFile} is empty, then the {@link #filesRegex} (<b>case-sensitive</b>) must contain
 * capturing group (<b>nested group is not allowed</b>),
 * and the target filename will be generated based on the contents of the capturing group,
 * then resources with the same target filename will be merged.
 * Otherwise, all matching resources are merged into the file represented by {@link #mergeFile}.
 * <p>
 * For example: The origin resource files are {@code base_message.properties},
 * {@code biz1_message.properties}, {@code base_message_en.properties},
 * {@code biz1_message_en.properties}.
 * If the {@link #mergeFile} is empty and the {@link #filesRegex} is {@code .*(message)(_.*)?(\.properties)},
 * then the target resource filename will be {@code message.properties} and {@code message_en.properties}.
 * If the {@link #mergeFile} is {@code xxx.yyy}, then it will just use value represented by {@link #mergeFile}.
 * <p>
 * Specially, if the target resource file is already exists in the origin resource files,
 * (In the above example it's {@code message.properties} and {@code message_en.properties}),
 * {@link #commentFormat} and {@link #deleteIfResourceBeenMerged} will be ignored,
 * that mean this origin resource file will not be deleted,
 * nor will additional comments be added in front of the content.
 * <p>
 *
 * @author xmcx
 * @see Pattern#compile(String)
 * @see String#format(String, Object...)
 * @since 2024.05.30
 */
@Slf4j
@Setter
@Getter
public class DefaultResourcesMergeStrategy implements ResourcesMergeStrategy {

    private static final boolean ON_WINDOWS = Os.isFamily(Os.FAMILY_WINDOWS);

    /**
     * The expression of build directory.
     */
    private static final String buildDirExpression = "${project.build.directory}";

    /**
     * The default value of origin directory. Relative to directory represented by {@link #buildDirExpression}.
     */
    private static final String defaultOriginDir = "classes";

    /**
     * The default value of origin directory. Relative to directory represented by {@link #buildDirExpression}.
     */
    private static final String defaultMergeDir = "generated-resources";

    /**
     * The regex (<b>case-sensitive</b>) of resources to be merged.
     *
     * @see Pattern#compile(String)
     */
    private String filesRegex;

    /**
     * Exclude special files.
     * <p>
     * It's allows you to avoid {@link #filesRegex} contains nested group in some case.
     */
    private Set<String> excludeFiles;

    /**
     * The origin directory of merging file.
     * Absolute directory or relative to directory represented by {@link #buildDirExpression}.
     * <p>
     * Default directory is {@link #defaultMergeDir}.
     *
     * @see #buildDirExpression
     * @see #defaultOriginDir
     */
    private String originDir;

    /**
     * The output directory of merged file.
     * Absolute directory or relative to directory represented by {@link #buildDirExpression}.
     * <p>
     * If {@link #useCommonRootDirByGroupIfOutputDirEmpty} is {@code true}, it will use
     * common root directory of the origin resource files which is merged into same resource file;
     * otherwise it will use {@link #defaultMergeDir} as default directory.
     *
     * @see #buildDirExpression
     * @see #defaultMergeDir
     * @see #useCommonRootDirByGroupIfOutputDirEmpty
     */
    private String mergeDir;

    /**
     * The filename of merged file.
     * <p>
     * If empty, the filename will be evaluated by {@link #filesRegex}.
     *
     * @see #filesRegex
     */
    private String mergeFile;

    /**
     * Number of newlines will be added before merge resource. A non-positive number means not to add newlines.
     * <p>
     * Default value is {@literal 2}.
     */
    private int numOfNewlinesBeforeMergeResource = 2;

    /**
     * The format of comment which added before resource merge.
     * <p>
     * Comment will be evaluated by using {@literal String.format(commentFormat, filename)}.
     * <p>
     * if {@code commentFormat} is empty, then no comment will be added.
     *
     * @see String#format(String, Object...)
     */
    private String commentFormat;

    /**
     * Whether the resource should be deleted after being merged.
     * <p>
     * Default value is {@code true}
     */
    private boolean deleteIfResourceBeenMerged = true;

    /**
     * Whether retry if delete failed.
     * <p>
     * Default value is {@code true}
     */
    private boolean retryIfDeleteFailed = true;

    /**
     * If output directory empty, use common root directory by group.
     * <p>
     * Default value is {@code true}
     */
    private boolean useCommonRootDirByGroupIfOutputDirEmpty = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void merge(MavenProject project, MavenSession session, MojoExecution mojoExecution)
            throws ResourcesMergeException {
        if (StringUtils.isEmpty(filesRegex)) {
            throw new ResourcesMergeException("filesRegex cannot be empty");
        }
        final Pattern filesPattern = Pattern.compile(filesRegex);
        final PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);
        final File buildDir = evaluateDir(evaluator, buildDirExpression);
        final File originDir = resolveDir(buildDir, this.originDir, defaultOriginDir);
        final File mergeDir;
        if (StringUtils.isNotEmpty(this.mergeDir)) {
            mergeDir = resolveDir(buildDir, this.mergeDir);
        } else {
            mergeDir = useCommonRootDirByGroupIfOutputDirEmpty ? null : resolveDir(buildDir, defaultMergeDir);
        }
        final int numOfNewlinesBeforeMergeResource = this.numOfNewlinesBeforeMergeResource;
        final String commentFormat = this.commentFormat;
        final boolean deleteIfResourceBeenMerged = this.deleteIfResourceBeenMerged;
        final boolean retryIfDeleteFailed = this.retryIfDeleteFailed;
        final String mergeFile = this.mergeFile;
        final String newlines = System.lineSeparator();
        final String separator = File.separator; // ? windows

        final Map<String, MergeGroupWrapper> mergeGroupMap;
        try (Stream<Path> pathStream = Files.walk(originDir.toPath())) {
            mergeGroupMap = pathStream
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> excludeFiles == null || !excludeFiles.contains(path.getFileName().toString()))
                    .map(path -> wrap(filesPattern, mergeFile, separator, path))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(MergeWrapper::getMergeFile, MergeGroupWrapper::of, MergeGroupWrapper::merge));
        } catch (IOException e) {
            throw new ResourcesMergeException("Cannot list files by directory: " + originDir.getAbsolutePath(), e);
        }

        mergeGroupMap.forEach((mf, mg) -> {
            final File dir;
            if (mergeDir == null) {
                dir = new File(String.join(separator, mg.getCommonRootPaths()));
            } else {
                dir = mergeDir;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new ResourcesMergeException("Cannot create resource output directory: " + dir.getAbsolutePath());
            }
            final File file = new File(dir, mf);
            final long l = file.length();
            try (FileOutputStream fileOutputStream = new FileOutputStream(file, true)) {
                for (int i = 0; i < mg.getOriginFiles().size(); i++) {
                    File originFile = mg.getOriginFiles().get(i);
                    if (Files.isSameFile(file.toPath(), originFile.toPath())) {
                        logDebug("Skipping resource '{}' is same to target '{}'", originFile.getAbsolutePath(), file.getAbsolutePath());
                        continue;
                    }
                    logDebug("Merging resource '{}' into target '{}'", originFile.getAbsolutePath(), file.getAbsolutePath());
                    if (numOfNewlinesBeforeMergeResource > 0
                            // not add newlines if target file is not exist and first merging
                            && (l > 0 || i > 0)) {
                        for (int j = 0; j < numOfNewlinesBeforeMergeResource; j++) {
                            fileOutputStream.write(newlines.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    if (StringUtils.isNotEmpty(commentFormat)) {
                        fileOutputStream.write(String.format(commentFormat, originFile.getName()).getBytes(StandardCharsets.UTF_8));
                    }
                    Files.copy(originFile.toPath(), fileOutputStream);
                    delete(originFile, deleteIfResourceBeenMerged, retryIfDeleteFailed);
                }
                logInfo("Merging {} resources into target '{}'", mg.getOriginFiles().size(), file.getAbsolutePath());
            } catch (IOException e) {
                throw new ResourcesMergeException("Cannot create resource file: " + file.getAbsolutePath(), e);
            }
        });
    }

    /**
     * Evaluate {@code expression} to file and create it if necessary.
     */
    private File evaluateDir(PluginParameterExpressionEvaluator evaluator, String expression) {
        String dirStr = evaluateString(evaluator, expression, StringUtils::isNotEmpty);
        File dir = new File(dirStr);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new ResourcesMergeException("Cannot create directory: " + dirStr);
        }
        return dir;
    }

    /**
     * Evaluator {@code expression} to string value if necessary.
     */
    private String evaluateString(PluginParameterExpressionEvaluator evaluator, String expression, Predicate<String> predicate) {
        String value;
        try {
            value = (String) evaluator.evaluate(expression);
        } catch (ExpressionEvaluationException e) {
            throw new ResourcesMergeException("Cannot evaluate value of expression: " + expression, e);
        }
        // notEmpty
        if (!predicate.test(value)) {
            throw new ResourcesMergeException("Cannot evaluate value of expression: " + expression);
        }
        logDebug("Evaluating expression '{}' to '{}'", expression, value);
        return value;
    }

    private void delete(File file, boolean deletable, boolean retryable) {
        if (!deletable || file.delete() || !file.exists()) {
            return;
        }

        if (retryable) {
            if (ON_WINDOWS) {
                // Refer to maven-clean-plugin: try to release any locks held by non-closed files
                System.gc();
            }
            final int[] delays = {50, 250, 750};
            for (int delay : delays) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                }
                if (file.delete() || !file.exists()) {
                    return;
                }
            }
        }

        throw new ResourcesMergeException("Cannot delete resource after been merged: " + file.getAbsolutePath());
    }

    /**
     * Wrap a file.
     *
     * @return {@code null} if the file represented by {@code path} should not be merged.
     */
    private MergeWrapper wrap(Pattern filesPattern, String mergeFile, String separator, Path path) {
        File file = path.toFile().getAbsoluteFile();
        String absolutePath = file.getAbsolutePath(); // use getCanonicalPath ?
        String filename = file.getName();
        Matcher matcher = filesPattern.matcher(filename);
        if (!matcher.matches()) {
            logDebug("Ignoring mismatched file '{}'", absolutePath);
            return null;
        }
        logDebug("Resolving file '{}'", absolutePath);
        String targetFilename;
        if (StringUtils.isEmpty(mergeFile)) {
            int gc = matcher.groupCount();
            if (gc <= 0) {
                throw new ResourcesMergeException("Cannot resolve merge targetFilename: " +
                        "filesRegex requires capturing-group or setting mergeFile");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= gc; i++) {
                String s = matcher.group(i);
                if (s != null) {
                    sb.append(s);
                }
            }
            targetFilename = sb.toString();
            if (StringUtils.isEmpty(targetFilename)) {
                throw new ResourcesMergeException("Cannot resolve merge targetFilename: " +
                        "filename '" + absolutePath + "' matched filesRegex '" + filesPattern.pattern() + "', " +
                        "but all of capturing-group's content are null, " +
                        "pls consider edit filesRegex or exclude this file");
            }
        } else {
            targetFilename = mergeFile;
        }
        // do not worry about path start with separator, it will be split into an empty string.
        String[] paths = absolutePath.split(separator);
        return new MergeWrapper(file, paths, targetFilename);
    }

    /**
     * Wrapper of file to be merged (avoid multiple uses {@link Pattern}).
     */
    @Getter
    static final class MergeWrapper {
        /**
         * Origin resource file.
         */
        private final File originFile;
        /**
         * Origin
         */
        private final List<String> paths;
        /**
         * Target resource filename.
         */
        private final String mergeFile;

        MergeWrapper(File originFile, String[] paths, String mergeFile) {
            this.originFile = originFile;
            this.paths = Collections.unmodifiableList(Arrays.asList(paths));
            this.mergeFile = mergeFile;
        }
    }

    /**
     * Wrapper of group files to be merged.
     */
    @Getter
    static final class MergeGroupWrapper {

        /**
         * Common root paths of {@link #originFiles}.
         *
         * @see #originFiles
         */
        private final List<String> commonRootPaths;

        /**
         * Origin files to be merged.
         */
        private final List<File> originFiles;

        MergeGroupWrapper(List<String> commonRootPaths, List<File> originFiles) {
            this.commonRootPaths = Collections.unmodifiableList(commonRootPaths);
            this.originFiles = Collections.unmodifiableList(originFiles);
        }

        /**
         * Convert single merge file to group.
         */
        static MergeGroupWrapper of(MergeWrapper w) {
            return new MergeGroupWrapper(w.getPaths(), Collections.singletonList(w.getOriginFile()));
        }

        /**
         * Merge two group to single with same common root paths.
         */
        static MergeGroupWrapper merge(MergeGroupWrapper a, MergeGroupWrapper b) {
            List<String> aPaths = a.getCommonRootPaths();
            List<String> bPaths = b.getCommonRootPaths();
            List<String> paths;
            int idx = 0;
            while (true) {
                if (aPaths.size() == idx || bPaths.size() == idx
                        || !aPaths.get(idx).equals(bPaths.get(idx))) {
                    paths = aPaths.subList(0, idx);
                    break;
                }
                idx++;
            }
            List<File> files = new ArrayList<>();
            files.addAll(a.getOriginFiles());
            files.addAll(b.getOriginFiles());
            return new MergeGroupWrapper(paths, files);
        }
    }

    /**
     * Resolve dir to absolute.
     */
    static File resolveDir(File parentDir, String child, String defChild) {
        String temp = StringUtils.isNotEmpty(child) ? child : defChild;
        return resolveDir(parentDir, temp);
    }

    /**
     * Resolve dir to absolute.
     */
    static File resolveDir(File parentDir, String child) {
        File tempDir = new File(child);
        if (!tempDir.isAbsolute()) {
            tempDir = new File(parentDir, child);
        }
        return tempDir;
    }

    /**
     * Debug log.
     */
    static void logDebug(String format, Object... arguments) {
        if (log.isDebugEnabled()) {
            log.debug(format, arguments);
        }
    }

    /**
     * Info log.
     */
    static void logInfo(String format, Object... arguments) {
        if (log.isInfoEnabled()) {
            log.info(format, arguments);
        }
    }

    /**
     * Error log.
     */
    static void logError(String format, Object... arguments) {
        if (log.isInfoEnabled()) {
            log.info(format, arguments);
        }
    }
}
