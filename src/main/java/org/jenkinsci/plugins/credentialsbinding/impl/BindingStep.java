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
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Workflow step to bind credentials.
 */
public final class BindingStep extends AbstractStepImpl {

    private final List<? extends MultiBinding<?>> bindings;

    @DataBoundConstructor public BindingStep(List<? extends MultiBinding<?>> bindings) {
        this.bindings = bindings;
    }

    public List<? extends MultiBinding<?>> getBindings() {
        return bindings;
    }

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @Inject(optional=true) private transient BindingStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        // TODO ideally we would like to just create a fresh EnvVars with only our own bindings.
        // But DefaultStepContext has no notion of merging multiple EnvVars instances across nested scopes.
        // So we need to pick up the bindings created by ExecutorStepExecution and append to them.
        // This has the unfortunate effect of “freezing” any custom values set via EnvActionImpl.setProperty for the duration of this step,
        // because these will also be present in the inherited EnvVars.
        @StepContextParameter private transient EnvVars env;

        @Override public boolean start() throws Exception {
            EnvVars overrides = new EnvVars(env);
            List<MultiBinding.Unbinder> unbinders = new ArrayList<MultiBinding.Unbinder>();
            for (MultiBinding<?> binding : step.bindings) {
                MultiBinding.MultiEnvironment environment = binding.bind(run, workspace, launcher, listener);
                unbinders.add(environment.getUnbinder());
                overrides.putAll(environment.getValues());
            }
            getContext().newBodyInvoker().withContext(overrides).withCallback(new Callback(unbinders)).start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            // should be no need to do anything special (but verify in JENKINS-26148)
        }

        // TODO in case [Workflow]Run gets some equivalent to getSensitiveBuildVariables, this should be implemented here somehow

    }

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

    }

}
