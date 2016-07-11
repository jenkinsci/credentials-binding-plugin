/*
 * The MIT License
 *
 * Copyright 2013 jglick.
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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.Run.RunnerAbortedException;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

@SuppressWarnings({"rawtypes", "unchecked"}) // inherited from BuildWrapper
public class SecretBuildWrapper extends BuildWrapper {

    private final List<? extends MultiBinding<?>> bindings;

    @DataBoundConstructor public SecretBuildWrapper(List<? extends MultiBinding<?>> bindings) {
        this.bindings = bindings;
    }
    
    public List<? extends MultiBinding<?>> getBindings() {
        return bindings;
    }

    @Override public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final List<MultiBinding.MultiEnvironment> m = Lists.transform(build.getActions(SharedMultiEnvAction.class),
                new Function<SharedMultiEnvAction, MultiBinding.MultiEnvironment>() {
                    public MultiBinding.MultiEnvironment apply (SharedMultiEnvAction action) {
                        return action.get();
                    }
        });

        return new Environment() {
            @Override public void buildEnvVars(Map<String,String> env) {
                for (MultiBinding.MultiEnvironment e : m) {
                    env.putAll(e.getValues());
                }
            }
            @Override public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                for (MultiBinding.MultiEnvironment e : m) {
                    e.getUnbinder().unbind(build, build.getWorkspace(), launcher, listener);
                }
                return true;
            }
        };
    }

    @Override public OutputStream decorateLogger(AbstractBuild build, OutputStream logger)
            throws IOException, InterruptedException, RunnerAbortedException {

        Map<String,String> overrides = new HashMap<String, String>();
        for (MultiBinding<?> binding : bindings) {
            MultiBinding.MultiEnvironment environment = binding.bind(build, null, null, null);
            build.addAction(new SharedMultiEnvAction(environment));
            for (String envKey : environment.getValues().keySet()) {
                if (!environment.getValues().get(envKey).isEmpty()) {
                    overrides.put(envKey, environment.getValues().get(envKey));
                }
            }
        }
        if (!overrides.isEmpty()) {
            return new BindingStep.Filter(overrides.values(), build.getCharset().name()).decorateLogger(build, logger);
        } else {
            return logger;
        }
    }

    @Override public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        for (MultiBinding binding : bindings) {
            sensitiveVariables.addAll(binding.variables());
        }
    }

    private static class SharedMultiEnvAction extends InvisibleAction {
        private final transient MultiBinding.MultiEnvironment me;

        public SharedMultiEnvAction(MultiBinding.MultiEnvironment me){
            this.me = me;
        }

        public MultiBinding.MultiEnvironment get() {
            return this.me;
        }
    }

    @Extension public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override public String getDisplayName() {
            return Messages.SecretBuildWrapper_use_secret_text_s_or_file_s_();
        }

    }

}
