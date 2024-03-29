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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.Symbol;

import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

public class UsernamePasswordMultiBinding extends MultiBinding<StandardUsernamePasswordCredentials> {

    private final String usernameVariable;
    private final String passwordVariable;

    @DataBoundConstructor public UsernamePasswordMultiBinding(String usernameVariable, String passwordVariable, String credentialsId) {
        super(credentialsId);
        this.usernameVariable = usernameVariable;
        this.passwordVariable = passwordVariable;
    }

    public String getUsernameVariable() {
        return usernameVariable;
    }

    public String getPasswordVariable() {
        return passwordVariable;
    }

    @Override protected Class<StandardUsernamePasswordCredentials> type() {
        return StandardUsernamePasswordCredentials.class;
    }

    @Override public MultiEnvironment bind(@NonNull Run<?, ?> build,
                                           @Nullable FilePath workspace,
                                           @Nullable Launcher launcher,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        StandardUsernamePasswordCredentials credentials = getCredentials(build);
        Map<String, String> secretValues = new LinkedHashMap<>();
        Map<String, String> publicValues = new LinkedHashMap<>();
        (credentials.isUsernameSecret() ? secretValues : publicValues).put(usernameVariable, credentials.getUsername());
        secretValues.put(passwordVariable, credentials.getPassword().getPlainText());
        return new MultiEnvironment(secretValues, publicValues);
    }

    @Override public Set<String> variables(@NonNull Run<?, ?> build) throws CredentialNotFoundException {
        StandardUsernamePasswordCredentials credentials = getCredentials(build);
        Set<String> vars = new LinkedHashSet<>();
        if (credentials.isUsernameSecret()) {
            vars.add(usernameVariable);
        }
        vars.add(passwordVariable);
        return vars;
    }

    @Symbol("usernamePassword")
    @Extension public static class DescriptorImpl extends BindingDescriptor<StandardUsernamePasswordCredentials> {

        @Override protected Class<StandardUsernamePasswordCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.UsernamePasswordMultiBinding_username_and_password();
        }

        @Override public boolean requiresWorkspace() {
            return false;
        }
    }

}
