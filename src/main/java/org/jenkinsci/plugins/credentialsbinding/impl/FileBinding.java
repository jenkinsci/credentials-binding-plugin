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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

public class FileBinding extends AbstractOnDiskBinding<FileCredentials> {

    @DataBoundConstructor public FileBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<FileCredentials> type() {
        return FileCredentials.class;
    }

    @Override protected final FilePath write(FileCredentials credentials, FilePath dir) throws IOException, InterruptedException {
        FilePath secret = dir.child(credentials.getFileName());
        secret.copyFrom(credentials.getContent());
        secret.chmod(0400);
        return secret;
    }

    @SuppressWarnings("unused")
    @Deprecated
    private static class UnbinderImpl implements Unbinder {
        private static final long serialVersionUID = 1;
        private final String dirName;

        private UnbinderImpl(String dirName) {
            this.dirName = dirName;
        }

        protected Object readResolve() {
            return new UnbindableDir.UnbinderImpl(dirName);
        }

        @Override
        public void unbind(@NonNull Run<?, ?> build,
                           @Nullable FilePath workspace,
                           @Nullable Launcher launcher,
                           @NonNull TaskListener listener) {
            // replaced by the UnbindableDir.UnbinderImpl implementation
        }
    }

    @Symbol("file")
    @Extension public static class DescriptorImpl extends BindingDescriptor<FileCredentials> {

        @Override protected Class<FileCredentials> type() {
            return FileCredentials.class;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.FileBinding_secret_file();
        }

    }

}
