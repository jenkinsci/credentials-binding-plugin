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

import hudson.Extension;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    @Override public Environment setUp(final AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {};
    }

    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream outputStream) throws IOException, InterruptedException, Run.RunnerAbortedException {
        List<String> passwords = new ArrayList<String>();
        for (MultiBinding binding : bindings) {
            List<String> environements = new ArrayList<String>();
            environements.addAll(binding.variables());

            for (Map.Entry<String, String> entry : binding.bind(build, build.getWorkspace()).getValues().entrySet()) {
                if (environements.contains(entry.getKey())) {
                    passwords.add(entry.getValue());
                }
            }

            passwords.addAll(binding.variables());
        }

        return new BindingStep.Filter(passwords).decorateLogger(build, outputStream);
    }

    @Override public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
        final List<MultiBinding.MultiEnvironment> multi = new ArrayList<MultiBinding.MultiEnvironment>();
        for (MultiBinding binding : bindings) {
            try {
                multi.add(binding.bind(build, build.getWorkspace()));
            } catch (IOException ex) {
                Logger.getLogger(SecretBuildWrapper.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(SecretBuildWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        for (MultiBinding.MultiEnvironment envs : multi) {
            variables.putAll(envs.getValues());
        }
    }

    @Override public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        for (MultiBinding binding : bindings) {
            sensitiveVariables.addAll(binding.variables());
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
