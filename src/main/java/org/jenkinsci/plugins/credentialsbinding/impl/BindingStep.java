/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.credentialsbinding.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Workflow step to bind credentials.
 */
@SuppressWarnings("rawtypes") // TODO DescribableHelper does not yet seem to handle List<? extends MultiBinding<?>> or even List<MultiBinding<?>>
public final class BindingStep extends Step {

    private final List<MultiBinding> bindings;

    @DataBoundConstructor public BindingStep(List<MultiBinding> bindings) {
        this.bindings = bindings;
    }

    public List<MultiBinding> getBindings() {
        return bindings;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution2(this, context);
    }

    /** @deprecated Only here for serial compatibility. */
    @Deprecated
    private static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @Override public boolean start() {
            throw new AssertionError();
        }

    }

    private static final class Execution2 extends GeneralNonBlockingStepExecution {

        private static final long serialVersionUID = 1;

        private transient BindingStep step;

        Execution2(@NonNull BindingStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override public boolean start() {
            run(this::doStart);
            return false;
        }
        
        private void doStart() throws Exception {
            Run<?,?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            FilePath workspace = getContext().get(FilePath.class);
            Launcher launcher = getContext().get(Launcher.class);

            Map<String,String> secretOverrides = new LinkedHashMap<>();
            Map<String,String> publicOverrides = new LinkedHashMap<>();
            List<MultiBinding.Unbinder> unbinders = new ArrayList<>();
            for (MultiBinding<?> binding : step.bindings) {
                if (binding.getDescriptor().requiresWorkspace() &&
                        (workspace == null || launcher == null)) {
                    throw new MissingContextVariableException(FilePath.class, step.getDescriptor());
                }
                MultiBinding.MultiEnvironment environment = binding.bind(run, workspace, launcher, listener);
                unbinders.add(environment.getUnbinder());
                secretOverrides.putAll(environment.getSecretValues());
                publicOverrides.putAll(environment.getPublicValues());
            }
            if (!secretOverrides.isEmpty()) {
                boolean unix = launcher == null || launcher.isUnix();
                listener.getLogger().println("Masking supported pattern matches of " + secretOverrides.keySet().stream().map(
                    v -> unix ? "$" + v : "%" + v + "%"
                ).collect(Collectors.joining(" or ")));
            }
            getContext().newBodyInvoker().
                    withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new Overrider(secretOverrides, publicOverrides))).
                    withContext(BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), new Filter(secretOverrides.values(), run.getCharset().name()))).
                    withCallback(new Callback2(unbinders)).
                    start();
        }

        private final class Callback2 extends TailCall {

            private static final long serialVersionUID = 1;

            private final List<MultiBinding.Unbinder> unbinders;

            Callback2(List<MultiBinding.Unbinder> unbinders) {
                this.unbinders = unbinders;
            }

            @Override protected void finished(StepContext context) throws Exception {
                new Callback(unbinders).finished(context);
            }

        }

    }

    private static final class Overrider extends EnvironmentExpander {

        private static final long serialVersionUID = 1;

        private final Map<String,Secret> overrides = new HashMap<>();
        private Map<String, String> publicOverrides;

        Overrider(Map<String,String> overrides, Map<String, String> publicOverrides) {
            for (Map.Entry<String,String> override : overrides.entrySet()) {
                this.overrides.put(override.getKey(), Secret.fromString(override.getValue()));
            }
            this.publicOverrides = publicOverrides;
        }

        @Override public void expand(@NonNull EnvVars env) {
            for (Map.Entry<String,Secret> override : overrides.entrySet()) {
                env.override(override.getKey(), override.getValue().getPlainText());
            }
            for (Map.Entry<String, String> override : publicOverrides.entrySet()) {
                env.override(override.getKey(), override.getValue());
            }
        }

        @NonNull
        @Override
        public Set<String> getSensitiveVariables() {
            return Collections.unmodifiableSet(overrides.keySet());
        }

        private Object readResolve() {
            if (publicOverrides == null) {
                publicOverrides = new HashMap<>();
            }
            return this;
        }
    }

    /** Similar to {@code MaskPasswordsOutputStream}. */
    private static final class Filter extends ConsoleLogFilter implements Serializable {

        private static final long serialVersionUID = 1;

        private final Secret pattern;
        private String charsetName;
        
        Filter(Collection<String> secrets, String charsetName) {
            pattern = Secret.fromString(SecretPatterns.getAggregateSecretPattern(secrets).pattern());
            this.charsetName = charsetName;
        }
        
        // To avoid de-serialization issues with newly added field (charsetName)
        private Object readResolve() throws ObjectStreamException {
            if (this.charsetName == null) {
                this.charsetName = StandardCharsets.UTF_8.name();
            }
            return this;
        }

        @Override public OutputStream decorateLogger(AbstractBuild _ignore, OutputStream logger) {
            return new SecretPatterns.MaskingOutputStream(logger, () -> Pattern.compile(pattern.getPlainText()), charsetName);
        }

    }

    private static final class Callback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 1;

        private final List<MultiBinding.Unbinder> unbinders;

        Callback(List<MultiBinding.Unbinder> unbinders) {
            this.unbinders = unbinders;
        }

        @Override protected void finished(StepContext context) throws Exception {
            Exception xx = null;

            for (MultiBinding.Unbinder unbinder : unbinders) {
                try {
                    unbinder.unbind(context.get(Run.class), context.get(FilePath.class), context.get(Launcher.class), context.get(TaskListener.class));
                } catch (Exception x) {
                    if (xx == null) {
                        xx = x;
                    } else {
                        xx.addSuppressed(x);
                    }
                }
            }
            if (xx != null) {
                throw xx;
            }
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "withCredentials";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Bind credentials to variables";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(TaskListener.class, Run.class)));
        }

    }

}
