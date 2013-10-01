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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.UUID;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ZipFileBinding extends Binding<FileCredentials> {

    @DataBoundConstructor public ZipFileBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<FileCredentials> type() {
        return FileCredentials.class;
    }

    @Override public Environment bind(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        FileCredentials credentials = getCredentials(build.getProject());
        FilePath secrets = build.getBuiltOn().getRootPath().child("secretFiles");
        final FilePath dir = secrets.child(UUID.randomUUID().toString());
        dir.mkdirs();
        secrets.chmod(/*0700*/448);
        final FilePath secret = dir.child(credentials.getFileName());
        secret.unzipFrom(credentials.getContent());
        return new Environment() {
            @Override public String value() {
                return secret.getRemote();
            }
            @Override public void unbind() throws IOException, InterruptedException {
                dir.deleteRecursive();
            }
        };
    }

    @Extension public static class DescriptorImpl extends BindingDescriptor<FileCredentials> {

        @Override protected Class<FileCredentials> type() {
            return FileCredentials.class;
        }

        @Override public String getDisplayName() {
            return Messages.ZipFileBinding_secret_zip_file();
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item owner, @QueryParameter String value) {
            for (FileCredentials c : CredentialsProvider.lookupCredentials(FileCredentials.class, owner, null, Collections.<DomainRequirement>emptyList())) {
                if (c.getId().equals(value)) {
                    try {
                        InputStream is = c.getContent();
                        byte[] data = new byte[4];
                        if (is.read(data) == 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("Not a ZIP file");
                        }
                    } catch (IOException x) {
                        return FormValidation.warning("Could not verify file format");
                    }
                }
            }
            return FormValidation.error("No such credentials");
        }

    }

}
