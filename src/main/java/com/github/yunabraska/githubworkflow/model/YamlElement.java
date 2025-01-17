package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.filterNodesRecursive;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.removeQuotes;
import static java.util.Optional.ofNullable;

@SuppressWarnings({"SameReturnValue", "unused"})
public class YamlElement {

    protected YamlElement parent;
    protected final int startIndexAbs;
    protected final int endIndexAbs;
    protected final String key;
    protected final String text;
    protected final List<YamlElement> children = new ArrayList<>();
    protected final WorkflowContext context;
    public static final String CURSOR_STRING = "IntellijIdeaRulezzz";

    public YamlElement(
            final int startIndexAbs,
            final int endIndexAbs,
            final String key,
            final String text,
            final boolean isRootElement
    ) {
        this.startIndexAbs = startIndexAbs;
        this.endIndexAbs = endIndexAbs;
        this.key = key;
        this.text = text;
        this.context = isRootElement ? new WorkflowContext(this) : null;
    }

    public static YamlElement createYamlElement(
            final int startIndexAbs,
            final int endIndexAbs,
            final String key,
            final String text
    ) {
        return new YamlElement(startIndexAbs, endIndexAbs, key, text, false);
    }

    public YamlElement parent() {
        return parent;
    }

    public List<YamlElement> children() {
        return children;
    }

    public YamlElement detachedParent(final YamlElement parent) {
        this.parent = parent;
        return this;
    }

    public YamlElement parent(final YamlElement parent) {
        ofNullable(parent).ifPresent(p -> {
            this.parent = parent;
            parent.children(this);
        });
        return this;
    }

    public YamlElement children(final YamlElement... children) {
        return children(children == null ? null : Arrays.asList(children));
    }

    public YamlElement children(final Collection<YamlElement> children) {
        ofNullable(children).orElseGet(Collections::emptyList).stream().filter(Objects::nonNull).filter(child -> !this.equals(child)).forEach(child -> {
            child.parent = this;
            if (!this.children.contains(child)) {
                this.children.add(child);
            }
        });
        return this;
    }

    public int startIndexAbs() {
        return startIndexAbs;
    }

    public int endIndexAbs() {
        return endIndexAbs;
    }

    public int startIndexRel() {
        return 0;
    }

    public int endIndexRel() {
        return endIndexAbs - startIndexAbs;
    }

    public String text() {
        return text;
    }

    public String textNoQuotes() {
        return removeQuotes(text);
    }

    public String textOrChildText() {
        return ofNullable(text()).orElseGet(this::childText);
    }

    public String textOrChildTextNoQuotes() {
        return ofNullable(textNoQuotes()).orElseGet(this::childTextNoQuotes);
    }

    public TextRange textRange() {
        return startIndexAbs > -1 && endIndexAbs >= startIndexAbs ? new TextRange(startIndexAbs(), endIndexAbs()) : null;
    }

    public String key() {
        return key;
    }

    public String id() {
        return this.child("id").map(YamlElement::childTextNoQuotes).orElse(null);
    }

    public String name() {
        return this.child("name").map(YamlElement::childTextNoQuotes).orElse(null);
    }

    public String uses() {
        return this.child(FIELD_USES).map(YamlElement::childTextNoQuotes).orElse(null);
    }

    public String path() {
        return ofNullable(parent).map(YamlElement::path).map(p -> p + "/").orElse("") + this.keyOrIdOrName();
    }

    public String keyOrIdOrName() {
        return ofNullable(ofNullable(this.key()).orElseGet(this::id)).orElseGet(this::name);
    }

    public String type() {
        return this.child("type").map(YamlElement::childTextNoQuotes).orElse(null);
    }

    public String description() {
        return ofNullable(this.child("description").orElseGet(() -> this.child("desc").orElse(null))).map(YamlElement::childTextNoQuotes).orElse(null);
    }

    public boolean required() {
        return child("required").map(YamlElement::childTextNoQuotes).map(Boolean::parseBoolean).orElse(false);
    }

    public String childDefault() {
        return child("default").map(YamlElement::childTextNoQuotes).orElse(null);
    }

    public int childIndex() {
        return parent == null ? -1 : this.parent().children().indexOf(this);
    }


    public WorkflowContext context() {
        return parent() == null? context : parent().context();
    }

    public YamlElement initContext() {
        ofNullable(root()).map(YamlElement::context).ifPresent(WorkflowContext::init);
        return this;
    }

    public YamlElement root() {
        return parent() == null? this : parent().root();
    }

    public Stream<YamlElement> allElements() {
        return Stream.concat(Stream.of(this), children().stream().flatMap(YamlElement::allElements));
    }

    public String childText() {
        return children.isEmpty() ? null : children.iterator().next().text();
    }

    public String childTextNoQuotes() {
        return removeQuotes(childText());
    }

    public List<YamlElement> findChildNodes(final Predicate<YamlElement> filter) {
        return filterNodesRecursive(this, filter, new ArrayList<>());
    }

    public Optional<YamlElement> child(final String key) {
        return this.children().stream().filter(child -> key != null && child.key() != null && key.equalsIgnoreCase(child.key())).findFirst();
    }

    public Optional<YamlElement> childId(final String id) {
        return this.children().stream().filter(child -> id != null && child.id() != null && id.equalsIgnoreCase(child.id())).findFirst();
    }

    public Optional<YamlElement> child(final Predicate<YamlElement> filter) {
        return this.children().stream().filter(filter).findFirst();
    }

    public Optional<YamlElement> findParent(final String key) {
        return key == null ? Optional.empty() : findParent(p -> key.equalsIgnoreCase(p.key()));
    }

    public Optional<YamlElement> findParent(final Predicate<YamlElement> filter) {
        final boolean result = parent != null && filter.test(parent);
        return result || parent == null ? ofNullable(parent) : parent.findParent(filter);
    }

    //GITHUB SPECIFIC FILTERS
    public String usesOrName() {
        return ofNullable(this.uses()).orElseGet(this::name);
    }

    public Optional<YamlElement> findParentJob() {
        return findParent(job -> ofNullable(job.parent()).map(YamlElement::key).filter(FIELD_JOBS::equals).isPresent());
    }

    public Optional<YamlElement> findParentStep() {
        return findParent(job -> ofNullable(job.parent()).map(YamlElement::key).filter(FIELD_STEPS::equals).isPresent());
    }


    public Optional<YamlElement> findParentOutput() {
        return findParent(outputs -> FIELD_OUTPUTS.equals(outputs.key()));
    }

    public Optional<YamlElement> findParentWith() {
        return findParent(outputs -> FIELD_WITH.equals(outputs.key()));
    }

    public Optional<YamlElement> findParentOn() {
        return findParent(outputs -> FIELD_ON.equals(outputs.key()));
    }

    public List<YamlElement> listSteps() {
        return findChildNodes(step -> ofNullable(step.parent()).map(YamlElement::key).filter(FIELD_STEPS::equals).isPresent());
    }

    public Set<String> needItems() {
        final Set<String> result = new HashSet<>();
        if (FIELD_NEEDS.equals(key())) {
            ofNullable(textOrChildTextNoQuotes()).ifPresent(result::add);
            children.stream().map(YamlElement::textOrChildTextNoQuotes).filter(Objects::nonNull).forEach(result::add);
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final YamlElement that = (YamlElement) o;
        return startIndexAbs == that.startIndexAbs && endIndexAbs == that.endIndexAbs && Objects.equals(key, that.key) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startIndexAbs, endIndexAbs, key, text);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "{"
                + "key='" + keyOrIdOrName() + '\''
                + ", text='" + textNoQuotes() + '\''
                + ", children=" + children().size()
                + '}';
    }
}
