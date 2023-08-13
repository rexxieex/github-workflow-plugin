package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.cachePath;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadAction;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.ACTION_CACHE;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.CACHE_TEN_MINUTES;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.WORKFLOW_CACHE;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.hasText;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.yamlOf;
import static java.util.Optional.ofNullable;

@SuppressWarnings("unused")
public class GitHubAction {

    private final Map<String, String> inputs = new ConcurrentHashMap<>();
    private final Map<String, String> outputs = new ConcurrentHashMap<>();
    private final AtomicLong expiration = new AtomicLong(0);
    //TODO: get Tags for autocompletion
    private final List<String> tags = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> ref = new AtomicReference<>(null);
    private final AtomicReference<String> slug = new AtomicReference<>(null);
    private final AtomicReference<String> sub = new AtomicReference<>(null);
    private final AtomicReference<String> actionName = new AtomicReference<>(null);
    private final AtomicReference<String> downloadUrl = new AtomicReference<>(null);
    private final AtomicReference<String> uses = new AtomicReference<>(null);
    private final AtomicBoolean isAvailable = new AtomicBoolean(false);
    private final AtomicBoolean isAction = new AtomicBoolean(false);
    private final AtomicBoolean isLocal = new AtomicBoolean(false);
    private static final Logger LOG = Logger.getInstance(GitHubAction.class);

    public static GitHubAction getGitHubAction(final String uses) {
        try {
            GitHubAction gitHubAction = ACTION_CACHE.getOrDefault(uses, null);
            if (gitHubAction == null || gitHubAction.expiration() < System.currentTimeMillis()) {
                ofNullable(gitHubAction).ifPresent(GitHubAction::deleteFile);
                gitHubAction = new GitHubAction(uses);
                ACTION_CACHE.put(uses, gitHubAction);
            }
            return gitHubAction;
        } catch (final Exception e) {
            return new GitHubAction(null);
        }
    }

    public Map<String, String> inputs() {
        return inputs;
    }

    public Map<String, String> outputs() {
        return outputs;
    }

    public long expiration() {
        return expiration.get();
    }

    public List<String> tags() {
        return tags;
    }

    public String ref() {
        return ref.get();
    }

    public String slug() {
        return slug.get();
    }

    public String actionName() {
        return actionName.get();
    }

    public String sub() {
        return sub.get();
    }

    public boolean isAvailable() {
        return isAvailable.get();
    }

    public boolean isAction() {
        return isAction.get();
    }

    public String toUrl() {
        return isAction.get() ? toActionYamlUrl() : toWorkflowYamlUrl();
    }

    public String marketplaceUrl() {
        return ("https://github.com/marketplace/" + slug.get());
    }

    private String toActionYamlUrl() {
        return (ref.get() != null && slug.get() != null && sub.get() != null) ? "https://github.com/" + slug.get() + "/blob/" + ref.get() + sub.get() + "/action.yml" : null;
    }

    private String toRawActionYamlUrl() {
        return (ref.get() != null && slug.get() != null && sub.get() != null) ? "https://raw.githubusercontent.com/" + slug.get() + "/" + ref.get() + sub.get() + "/action.yml" : null;
    }

    private String toWorkflowYamlUrl() {
        return (ref.get() != null && slug.get() != null) ? "https://github.com/" + slug.get() + "/blob/" + ref.get() + "/.github/workflows/" + actionName : null;
    }

    private String toRawWorkflowYamlUrl() {
        return (ref.get() != null && slug.get() != null) ? "https://raw.githubusercontent.com/" + slug.get() + "/" + ref.get() + "/.github/workflows/" + actionName : null;
    }

    private String toGitHubUrl() {
        return (slug.get() != null && ref.get() != null) ? "https://github.com/" + slug.get() + "/tree" + ref.get() : null;
    }

    private GitHubAction(final String uses) {
        if (uses != null) {
            //TODO: better name extraction
            final int tagIndex = uses.indexOf("@");
            final int userNameIndex = uses.indexOf("/");
            final int repoNameIndex = uses.indexOf("/", userNameIndex + 1);
            if (uses.startsWith("./") && uses.contains(".github")) {
                isLocal.set(true);
            }

            this.uses.set(uses);
            if (tagIndex != -1 && userNameIndex < tagIndex) {
                ref.set(uses.substring(tagIndex + 1));
                slug.set(uses.substring(0, repoNameIndex > 0 ? repoNameIndex : tagIndex));
                if (uses.contains(".yaml") || uses.contains(".yml")) {
                    isAction.set(false);
                    actionName.set(uses.substring(uses.lastIndexOf("/") + 1, tagIndex));
                    downloadUrl.set(isLocal.get() ? uses : toRawWorkflowYamlUrl());
//                    setActionParameters(toRawWorkflowYamlUrl());
                } else {
                    isAction.set(true);
                    sub.set(repoNameIndex < tagIndex && repoNameIndex > 0 ? "/" + uses.substring(repoNameIndex + 1, tagIndex) : "");
                    actionName.set(uses.substring(userNameIndex + 1, tagIndex));
                    downloadUrl.set(isLocal.get() ? uses : toRawActionYamlUrl());
//                    setActionParameters(toActionYamlUrl());
                }
            }
        }
    }

    public void resolve(final Project project) {
        if (!isAvailable.get()) {
            setActionParameters(project, downloadUrl.get());
        }
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public void deleteCache() {
        isAvailable.set(false);
        WORKFLOW_CACHE.remove(workFlowCacheId());
        deleteFile();
    }

    private void deleteFile() {
        Optional.of(cachePath(this)).filter(Files::exists).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (final IOException ignored) {
                // ignored
            }
        });
    }

    private void setActionParameters(final Project project, final String downloadUrl) {
        try {
//            if (isLocal.get()) {
//                final VirtualFile virtualFile = ProjectUtil.guessProjectDir(project);
//                ProjectUtil.guessProjectDir(project).findFileByRelativePath(downloadUrl.substring(downloadUrl.indexOf(".github")));
//                final VirtualFile baseDir = project.getBaseDir();
//                expiration.set(System.currentTimeMillis() + CACHE_TEN_MINUTES / 10);
//            } else {
            extractActionParameters(project, downloadAction(downloadUrl, this));
//            }
        } catch (final Exception e) {
            LOG.warn("Failed to set parameters [" + this.uses.get() + "]", e);
            isAvailable.set(false);
            expiration.set(System.currentTimeMillis() + CACHE_TEN_MINUTES);
        }
    }

    private void extractActionParameters(final Project project, final String content) {
        isAvailable.set(hasText(content));
        expiration.set(System.currentTimeMillis() + (hasText(content) ? CACHE_ONE_DAY : CACHE_TEN_MINUTES));
        final WorkflowContext context = contextOf(project, workFlowCacheId(), content);
        inputs.putAll(getActionParameters(context, FIELD_INPUTS, isAction.get()));
        outputs.putAll(getActionParameters(context, FIELD_OUTPUTS, isAction.get()));
    }

    private String workFlowCacheId() {
        return actionName() + "_" + ref();
    }

    private Map<String, String> getActionParameters(final WorkflowContext context, final String nodeKey, final boolean action) {
        return context.root()
                .findChildNodes(child ->
                        (ofNullable(child.parent()).filter(parent -> nodeKey.equals(parent.key())).isPresent())
                                && (action || ofNullable(child.parent()).map(YamlElement::parent).map(YamlElement::parent).filter(parent -> FIELD_ON.equals(parent.key())).isPresent())
                )
                .stream()
                .filter(child -> hasText(child.keyOrIdOrName()))
                .collect(Collectors.toMap(YamlElement::keyOrIdOrName, GitHubWorkflowUtils::getDescription, (existing, replacement) -> existing));
    }

    private WorkflowContext contextOf(final Project project, final String key, final String text) {
        // READ CONTEXT
        final AtomicReference<WorkflowContext> contextRef = new AtomicReference<>();
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                final WorkflowContext context = yamlOf(PsiFileFactory.getInstance(project).createFileFromText(key, FileTypeManager.getInstance().getFileTypeByExtension("yaml"), text)).context().init();
                contextRef.set(context);
            } catch (final Exception e) {
                final WorkflowContext defaultValue = new YamlElement(-1, -1, null, null, null, null).context().init();
                contextRef.set(key == null ? defaultValue : WORKFLOW_CACHE.getOrDefault(key, defaultValue));
            }
        });

        final WorkflowContext context = contextRef.get();
        if (context != null && key != null) {
            WORKFLOW_CACHE.put(key, context);
        }

        return context;
    }

}
