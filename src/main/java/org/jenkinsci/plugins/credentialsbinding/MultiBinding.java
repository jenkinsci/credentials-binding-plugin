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

package org.jenkinsci.plugins.credentialsbinding;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A way of binding a kind of credentials to an environment variable during a build.
 * @param <C> a kind of credentials
 */
public abstract class MultiBinding<C extends StandardCredentials> extends AbstractDescribableImpl<MultiBinding<C>> implements ExtensionPoint {

    private final String credentialsId;

    /** For use with {@link DataBoundConstructor}. */
    protected MultiBinding(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    /** Type token. */
    protected abstract Class<C> type();

    /** Identifier of the credentials to be bound. */
    public final String getCredentialsId() {
        return credentialsId;
    }

    // TODO perhaps this should be split into a struct with a values map, accessible immediately, and an unbinder, which needs to hold serializable state?
    /** Callback for processing during a build. */
    public interface MultiEnvironment extends Serializable {

        /** Produces the value of the environment variables. */
        Map<String,String> values();

        /** Performs any needed cleanup. */
        void unbind(@Nonnull Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

    }

    /** Sets up bindings for a build. */
    public abstract MultiEnvironment bind(@Nonnull Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

    /** Defines keys expected to be set in {@link MultiEnvironment#values}, particularly any that might be sensitive. */
    public abstract Set<String> variables();

    /**
     * Looks up the actual credentials.
     * @param build the build.
     * @return the credentials
     * @throws FileNotFoundException if the credentials could not be found (for convenience, rather than returning null)
     */
    protected final @Nonnull C getCredentials(@Nonnull Run<?,?> build) throws IOException {
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
