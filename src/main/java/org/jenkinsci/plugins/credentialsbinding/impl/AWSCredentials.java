/*
 * The MIT License
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
import hudson.model.Run;
import java.io.IOException;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;

public class AWSCredentials extends MultiBinding<AWSCredentialsImpl> {

    @DataBoundConstructor public AWSCredentials(String credentialsId) {
        super(credentialsId);
    }

    @Override protected Class<AWSCredentialsImpl> type() {
        return AWSCredentialsImpl.class;
    }

    @Override public MultiEnvironment bind(Run<?, ?> build, FilePath workspace) throws IOException, InterruptedException {
        AWSCredentialsImpl credentials = getCredentials(build);
        Map<String,String> m = new HashMap<String,String>();
        m.put("AWS_ACCESS_KEY_ID", credentials.getAccessKey());
        m.put("AWS_SECRET_ACCESS_KEY", credentials.getSecretKey().getPlainText());
        return new MultiEnvironment(m);
    }

    @Override public Set<String> variables() {
        return new HashSet<String>(Arrays.asList("AWS_SECRET_ACCESS_KEY"));
    }

    @Extension public static class DescriptorImpl extends BindingDescriptor<AWSCredentialsImpl> {
        @Override protected Class<AWSCredentialsImpl> type() {
            return AWSCredentialsImpl.class;
        }

        @Override public String getDisplayName() {
            return Messages.AWSCredentials_aws_credentials();
        }

    }
}
