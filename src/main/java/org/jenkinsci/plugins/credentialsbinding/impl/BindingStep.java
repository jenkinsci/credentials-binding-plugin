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

import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Workflow step to bind credentials.
 */
@SuppressWarnings("rawtypes") // TODO DescribableHelper does not yet seem to handle List<? extends MultiBinding<?>> or even List<MultiBinding<?>>
public final class BindingStep extends AbstractStepImpl {

    private final List<MultiBinding> bindings;

    @DataBoundConstructor public BindingStep(List<MultiBinding> bindings) {
        this.bindings = bindings;
    }

    public List<MultiBinding> getBindings() {
        return bindings;
    }

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @Inject(optional=true) private transient BindingStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;

        @Override public boolean start() throws Exception {
            Map<String,String> overrides = new HashMap<String,String>();
            List<MultiBinding.Unbinder> unbinders = new ArrayList<MultiBinding.Unbinder>();
            for (MultiBinding<?> binding : step.bindings) {
                MultiBinding.MultiEnvironment environment = binding.bind(run, workspace, launcher, listener);
                unbinders.add(environment.getUnbinder());
                overrides.putAll(environment.getValues());
            }
            getContext().newBodyInvoker().
                    withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new Overrider(overrides))).
                    withContext(BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), new Filter(overrides.values(), run.getCharset().name()))).
                    withCallback(new Callback(unbinders)).
                    start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            // should be no need to do anything special (but verify in JENKINS-26148)
        }

    }

    private static final class Overrider extends EnvironmentExpander {

        private static final long serialVersionUID = 1;

        private final Map<String,Secret> overrides = new HashMap<String,Secret>();

        Overrider(Map<String,String> overrides) {
            for (Map.Entry<String,String> override : overrides.entrySet()) {
                this.overrides.put(override.getKey(), Secret.fromString(override.getValue()));
            }
        }

        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            for (Map.Entry<String,Secret> override : overrides.entrySet()) {
                env.override(override.getKey(), override.getValue().getPlainText());
            }
        }

    }

    /** Similar to {@code MaskPasswordsOutputStream}. */
    private static final class Filter extends ConsoleLogFilter implements Serializable {

        private static final long serialVersionUID = 1;

        private final Secret pattern;
        private String charsetName;
        
        Filter(Collection<String> secrets, String charsetName) {
            StringBuilder b = new StringBuilder();
            for (String secret : secrets) {
                if (b.length() > 0) {
                    b.append('|');
                }
                b.append(Pattern.quote(secret));
            }
            pattern = Secret.fromString(b.toString());
            this.charsetName = charsetName;
        }
        
        // To avoid de-serialization issues with newly added field (charsetName)
        private Object readResolve() throws ObjectStreamException {
            if (this.charsetName == null) {
                this.charsetName = Charsets.UTF_8.name();
            }
            return this;
        }

        @Override public OutputStream decorateLogger(AbstractBuild _ignore, final OutputStream logger) throws IOException, InterruptedException {
            final Pattern p = Pattern.compile(pattern.getPlainText());
            return new LineTransformationOutputStream() {
                @Override protected void eol(byte[] b, int len) throws IOException {
                    Matcher m = p.matcher(new String(b, 0, len, charsetName));
                    if (m.find()) {
                        logger.write(m.replaceAll("****").getBytes(charsetName));
                    } else {
                        // Avoid byte → char → byte conversion unless we are actually doing something.
                        logger.write(b, 0, len);
                    }
                }
            };
        }

    }

    // TODO use BodyExecutionCallback.TailCall from https://github.com/jenkinsci/workflow-plugin/pull/168
    private static final class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1;

        private final List<MultiBinding.Unbinder> unbinders;

        Callback(List<MultiBinding.Unbinder> unbinders) {
            this.unbinders = unbinders;
        }

        private void cleanup(StepContext context) {
            for (MultiBinding.Unbinder unbinder : unbinders) {
                try {
                    unbinder.unbind(context.get(Run.class), context.get(FilePath.class), context.get(Launcher.class), context.get(TaskListener.class));
                } catch (Exception x) {
                    context.onFailure(x);
                }
            }
        }

        @Override public void onSuccess(StepContext context, Object result) {
            cleanup(context);
            context.onSuccess(result);
        }

        @Override public void onFailure(StepContext context, Throwable t) {
            cleanup(context);
            context.onFailure(t);
        }

    }

    @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "withCredentials";
        }

        @Override public String getDisplayName() {
            return "Bind credentials to variables";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

}
