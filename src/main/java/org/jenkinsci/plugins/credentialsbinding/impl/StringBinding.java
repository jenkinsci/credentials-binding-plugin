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
import hudson.model.BuildListener;
import java.io.IOException;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

public class StringBinding extends Binding<StringCredentials> {

    @DataBoundConstructor public StringBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<StringCredentials> type() {
        return StringCredentials.class;
    }

    @Override public Environment bind(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final String value = getCredentials(build.getProject()).getSecret().getPlainText();
        return new Environment() {
            @Override public String value() {
                return value;
            }
            @Override public void unbind() throws IOException, InterruptedException {}
        };
    }

    @Extension public static class DescriptorImpl extends BindingDescriptor<StringCredentials> {

        @Override protected Class<StringCredentials> type() {
            return StringCredentials.class;
        }

        @Override public String getDisplayName() {
            return Messages.StringBinding_secret_text();
        }

    }

}
