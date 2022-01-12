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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class ZipFileBinding extends AbstractOnDiskBinding<FileCredentials> {

    @DataBoundConstructor public ZipFileBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<FileCredentials> type() {
        return FileCredentials.class;
    }

    @Override protected final FilePath write(FileCredentials credentials, FilePath dir) throws IOException, InterruptedException {
        FilePath secret = dir.child(credentials.getFileName());
        secret.unzipFrom(credentials.getContent());
        secret.chmod(0700); // note: it's a directory
        return secret;
    }

    @Symbol("zip")
    @Extension public static class DescriptorImpl extends BindingDescriptor<FileCredentials> {

        @Override protected Class<FileCredentials> type() {
            return FileCredentials.class;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ZipFileBinding_secret_zip_file();
        }

        // @RequirePOST
        public FormValidation doCheckCredentialsId(StaplerRequest req, @AncestorInPath Item owner, @QueryParameter String value) {
            //TODO due to weird behavior in c:select, there are initial calls using GET
            // so using this approach will prevent 405 errors
            if (!req.getMethod().equals("POST")) {
                return FormValidation.ok();
            }
            if (owner == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!owner.hasPermission(Item.EXTENDED_READ) && !owner.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            for (FileCredentials c : CredentialsProvider.lookupCredentials(FileCredentials.class, owner, null, Collections.emptyList())) {
                if (c.getId().equals(value)) {
                    InputStream is = null;
                    try {
                        is = c.getContent();
                        byte[] data = new byte[4];
                        if (is.read(data) == 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error(Messages.ZipFileBinding_NotZipFile());
                        }
                    } catch (IOException x) {
                        return FormValidation.warning(Messages.ZipFileBinding_CouldNotVerifyFileFormat());
                    }
                    finally {
                        if (is != null) {
                            IOUtils.closeQuietly(is);
                        }
                    }
                }
            }
            return FormValidation.error(Messages.ZipFileBinding_NoSuchCredentials());
        }

    }

}
