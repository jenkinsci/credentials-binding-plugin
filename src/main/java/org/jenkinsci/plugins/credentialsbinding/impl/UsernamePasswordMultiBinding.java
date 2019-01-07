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

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.Symbol;

import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.kohsuke.stapler.DataBoundSetter;

public class UsernamePasswordMultiBinding extends MultiBinding<StandardUsernamePasswordCredentials> {

    private String usernameVariable;
    private final String passwordVariable;

    @DataBoundConstructor public UsernamePasswordMultiBinding(String passwordVariable, String credentialsId) {
        super(credentialsId);
        this.passwordVariable = passwordVariable;
    }

    @Deprecated
    public UsernamePasswordMultiBinding(String usernameVariable, String passwordVariable, String credentialsId) {
        this(passwordVariable, credentialsId);
        setUsernameVariable(usernameVariable);
    }

    public String getUsernameVariable() {
        return usernameVariable;
    }

    @DataBoundSetter public void setUsernameVariable(String usernameVariable) {
        this.usernameVariable = Util.fixEmptyAndTrim(usernameVariable);
    }

    public String getPasswordVariable() {
        return passwordVariable;
    }

    @Override protected Class<StandardUsernamePasswordCredentials> type() {
        return StandardUsernamePasswordCredentials.class;
    }

    @Override public MultiEnvironment bind(@Nonnull Run<?, ?> build,
                                           @Nullable FilePath workspace,
                                           @Nullable Launcher launcher,
                                           @Nonnull TaskListener listener) throws IOException, InterruptedException {
        StandardUsernamePasswordCredentials credentials = getCredentials(build);
        Map<String,String> m = new HashMap<>();
        if (usernameVariable != null) {
            m.put(usernameVariable, credentials.getUsername());
        }
        m.put(passwordVariable, credentials.getPassword().getPlainText());
        return new MultiEnvironment(m);
    }

    @Override public Set<String> variables() {
        return usernameVariable != null ? ImmutableSet.of(usernameVariable, passwordVariable) : Collections.singleton(passwordVariable);
    }

    @Symbol("usernamePassword")
    @Extension public static class DescriptorImpl extends BindingDescriptor<StandardUsernamePasswordCredentials> {

        @Override protected Class<StandardUsernamePasswordCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }

        @Override public String getDisplayName() {
            return Messages.UsernamePasswordMultiBinding_username_and_password();
        }

        @Override public boolean requiresWorkspace() {
            return false;
        }
    }

}
