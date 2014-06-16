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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.kohsuke.stapler.DataBoundConstructor;

public class SecretBuildWrapper extends BuildWrapper {

    private final List<Binding<?>> bindings;

    @DataBoundConstructor public SecretBuildWrapper(List<Binding<?>> bindings) {
        this.bindings = bindings;
    }
    
    public List<Binding<?>> getBindings() {
        return bindings;
    }

    @Override public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final Map<String,Binding.Environment> m = new HashMap<String,Binding.Environment>();
        for (Binding binding : bindings) {
            m.put(binding.getVariable(), binding.bind(build, launcher, listener));
        }
        return new Environment() {
            @Override public void buildEnvVars(Map<String,String> env) {
                for (Map.Entry<String,Binding.Environment> entry : m.entrySet()) {
                    env.put(entry.getKey(), entry.getValue().value());
                }
            }
            @Override public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                for (Binding.Environment env : m.values()) {
                    env.unbind();
                }
                return true;
            }
        };
    }

    @Override public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        for (Binding binding : bindings) {
            sensitiveVariables.add(binding.getVariable());
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
