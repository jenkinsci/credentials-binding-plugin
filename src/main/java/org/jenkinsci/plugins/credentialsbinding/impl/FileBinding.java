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
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import java.util.UUID;
import org.jenkinsci.Symbol;

import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FileBinding extends Binding<FileCredentials> {

    @DataBoundConstructor public FileBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<FileCredentials> type() {
        return FileCredentials.class;
    }

    @Override public SingleEnvironment bindSingle(@Nonnull Run<?,?> build,
                                                  @Nullable FilePath workspace,
                                                  @Nullable Launcher launcher,
                                                  @Nonnull TaskListener listener) throws IOException, InterruptedException {
        FileCredentials credentials = getCredentials(build);
        FilePath secrets = secretsDir(workspace);
        if (secrets == null) {
            throw new IOException("Can't proceed with null workspace");
        } else {
            String dirName = UUID.randomUUID().toString();
            final FilePath dir = secrets.child(dirName);
            dir.mkdirs();
            secrets.chmod(/*0700*/448);
            FilePath secret = dir.child(credentials.getFileName());
            copy(secret, credentials);
            if (secret.isDirectory()) { /* ZipFileBinding */
                // needs to be writable so we can delete its contents
                // needs to be executable so we can list the contents
                secret.chmod(0700);
            } else {
                secret.chmod(0400);
            }
            return new SingleEnvironment(secret.getRemote(), new UnbinderImpl(dirName));
        }
    }
    
    private static class UnbinderImpl implements Unbinder {

        private static final long serialVersionUID = 1;

        private final String dirName;
        
        UnbinderImpl(String dirName) {
            this.dirName = dirName;
        }
        
        @Override public void unbind(@Nonnull Run<?, ?> build,
                                     @Nullable FilePath workspace,
                                     @Nullable Launcher launcher,
                                     @Nonnull TaskListener listener) throws IOException, InterruptedException {
            secretsDir(workspace).child(dirName).deleteRecursive();
        }
        
    }

    @CheckForNull
    private static FilePath secretsDir(@CheckForNull FilePath workspace) {
        if (workspace != null) {
            return tempDir(workspace).child("secretFiles");
        } else {
            return null;
        }
    }

    // TODO 1.652 use WorkspaceList.tempDir
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    protected void copy(FilePath secret, FileCredentials credentials) throws IOException, InterruptedException {
        secret.copyFrom(credentials.getContent());
    }

    @Symbol("file")
    @Extension public static class DescriptorImpl extends BindingDescriptor<FileCredentials> {

        @Override protected Class<FileCredentials> type() {
            return FileCredentials.class;
        }

        @Override public String getDisplayName() {
            return Messages.FileBinding_secret_file();
        }

    }

}
