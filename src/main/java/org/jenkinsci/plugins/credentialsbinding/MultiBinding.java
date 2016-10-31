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

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
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

    /** Result of {@link #bind}. */
    public static final class MultiEnvironment implements Serializable {

        private final Map<String,String> values;
        private final Unbinder unbinder;

        public MultiEnvironment(Map<String,String> values) {
            this(values, new NullUnbinder());
        }

        public MultiEnvironment(Map<String,String> values, Unbinder unbinder) {
            this.values = new HashMap<String,String>(values);
            this.unbinder = unbinder;
        }

        public Map<String,String> getValues() {
            return Collections.unmodifiableMap(values);
        }

        public Unbinder getUnbinder() {
            return unbinder;
        }

    }

    /** Callback run at the end of a build. */
    public interface Unbinder extends Serializable {
        /** Performs any needed cleanup. */
        void unbind(@Nonnull Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;
    }

    /** No-op callback. */
    protected static final class NullUnbinder implements Unbinder {
        private static final long serialVersionUID = 1;
        @Override public void unbind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {}
    }

    /** Sets up bindings for a build. */
    public abstract MultiEnvironment bind(@Nonnull Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

    /** Defines keys expected to be set in {@link MultiEnvironment#getValues}, particularly any that might be sensitive. */
    public abstract Set<String> variables();

    /**
     * Looks up the actual credentials.
     * @param build the build.
     * @return the credentials
     * @throws FileNotFoundException if the credentials could not be found (for convenience, rather than returning null)
     */
    protected final @Nonnull C getCredentials(@Nonnull Run<?,?> build) throws IOException {
        IdCredentials cred = CredentialsProvider.findCredentialById(credentialsId, IdCredentials.class, build);
        if (cred==null)
            throw new CredentialNotFoundException(credentialsId);

        if (type().isInstance(cred))
            return type().cast(cred);

        
        Descriptor expected = Jenkins.getActiveInstance().getDescriptor(type());
        throw new CredentialNotFoundException("Credentials '"+credentialsId+"' is of type '"+
                cred.getDescriptor().getDisplayName()+"' where '"+
                (expected!=null ? expected.getDisplayName() : type().getName())+
                "' was expected");
    }

    @Override public BindingDescriptor<C> getDescriptor() {
        return (BindingDescriptor<C>) super.getDescriptor();
    }

    /**
     * Utility method for turning a collection of secret strings into a {@link Secret}.
     * @param secrets A collection of secret strings
     * @return A {@link Secret} generated from that collection.
     */
    public static Secret getSecretForStrings(Collection<String> secrets) {
        StringBuilder b = new StringBuilder();
        for (String secret : secrets) {
            if (b.length() > 0) {
                b.append('|');
            }
            b.append(Pattern.quote(secret));
        }
        return Secret.fromString(b.toString());
    }
}
