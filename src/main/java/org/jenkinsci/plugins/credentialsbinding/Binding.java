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

package org.jenkinsci.plugins.credentialsbinding;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Item;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import javax.annotation.Nonnull;

import hudson.model.Job;
import hudson.security.ACL;
import hudson.util.VariableResolver;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A way of binding a kind of credentials to an environment variable during a build.
 * @param <C> a kind of credentials
 */
public abstract class Binding<C extends StandardCredentials> extends AbstractDescribableImpl<Binding<C>> implements ExtensionPoint {

    private final String variable;
    private final String credentialsId;

    /** For use with {@link DataBoundConstructor}. */
    protected Binding(String variable, String credentialsId) {
        this.variable = variable;
        this.credentialsId = credentialsId;
    }

    /** Type token. */
    protected abstract Class<C> type();

    /** Environment variable name. */
    public String getVariable() {
        return variable;
    }

    /** Identifier of the credentials to be bound. */
    public String getCredentialsId() {
        return credentialsId;
    }

    /** Callback for processing during a build. */
    public interface Environment {

        /** Produces the value of the environment variable. */
        String value();

        /** Performs any needed cleanup. */
        void unbind() throws IOException, InterruptedException;

    }

    /** Sets up bindings for a build. */
    public abstract Environment bind(@Nonnull AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;

    /**
     * Looks up the actual credentials.
     * @param build the build.
     * @return the credentials
     * @throws FileNotFoundException if the credentials could not be found (for convenience, rather than returning null)
     */
    protected final @Nonnull C getCredentials(@Nonnull AbstractBuild<?,?> build) throws IOException {
        C c = CredentialsProvider.findCredentialById(credentialsId, type(), build);
        if (c != null) {
            return c;
        }
        throw new FileNotFoundException(credentialsId);
    }

    @Override public BindingDescriptor<C> getDescriptor() {
        return (BindingDescriptor<C>) super.getDescriptor();
    }

}
