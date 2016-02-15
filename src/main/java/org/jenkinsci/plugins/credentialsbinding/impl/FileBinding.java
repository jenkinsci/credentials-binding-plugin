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
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.UUID;

import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

public class FileBinding extends Binding<FileCredentials> {

    @DataBoundConstructor public FileBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<FileCredentials> type() {
        return FileCredentials.class;
    }

    @Override public SingleEnvironment bindSingle(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        FileCredentials credentials = getCredentials(build);
        FilePath secrets = secretsDir(workspace);
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
        }
        else {
            secret.chmod(0400);
        }
        return new SingleEnvironment(secret.getRemote(), new UnbinderImpl(dirName));
    }
    
    private static class UnbinderImpl implements Unbinder {

        private static final long serialVersionUID = 1;

        private final String dirName;
        
        UnbinderImpl(String dirName) {
            this.dirName = dirName;
        }
        
        @Override public void unbind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            secretsDir(workspace).child(dirName).deleteRecursive();
        }
        
    }

    static FilePath secretsDir(FilePath workspace) {
        Computer computer = workspace.toComputer();
        Node node = computer == null ? null : computer.getNode();
        FilePath root = node == null ? workspace : node.getRootPath();
        return root.child("secretFiles");
    }

    protected void copy(FilePath secret, FileCredentials credentials) throws IOException, InterruptedException {
        secret.copyFrom(credentials.getContent());
    }

    @Extension public static class DescriptorImpl extends BindingDescriptor<FileCredentials> {

        @Override protected Class<FileCredentials> type() {
            return FileCredentials.class;
        }

        @Override public String getDisplayName() {
            return Messages.FileBinding_secret_file();
        }

    }

}
