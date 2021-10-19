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
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
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

        private static final long serialVersionUID = 1;

        @Deprecated
        private transient Map<String,String> values;
        private Map<String,String> secretValues;
        private Map<String,String> publicValues;
        private final Unbinder unbinder;

        public MultiEnvironment(Map<String,String> secretValues) {
            this(secretValues, Collections.emptyMap());
        }

        public MultiEnvironment(Map<String,String> secretValues, Map<String,String> publicValues) {
            this(secretValues, publicValues, new NullUnbinder());
        }

        public MultiEnvironment(Map<String,String> secretValues, Unbinder unbinder) {
            this(secretValues, Collections.emptyMap(), unbinder);
        }

        public MultiEnvironment(Map<String,String> secretValues, Map<String,String> publicValues, Unbinder unbinder) {
            this.values = null;
            this.secretValues = new LinkedHashMap<>(secretValues);
            this.publicValues = new LinkedHashMap<>(publicValues);
            if (!CollectionUtils.intersection(secretValues.keySet(), publicValues.keySet()).isEmpty()) {
                throw new IllegalArgumentException("Cannot use the same key in both secretValues and publicValues");
            }
            this.unbinder = unbinder;
        }

        // To avoid de-serialization issues with newly added field (secretValues, publicValues)
        private Object readResolve() {
            if (values != null) {
                secretValues = values;
                publicValues = Collections.emptyMap();
                values = null;
            }
            return this;
        }

        @Deprecated
        public Map<String,String> getValues() {
            return Collections.unmodifiableMap(secretValues);
        }

        public Map<String,String> getSecretValues() {
            return Collections.unmodifiableMap(secretValues);
        }

        public Map<String,String> getPublicValues() {
            return Collections.unmodifiableMap(publicValues);
        }

        public Unbinder getUnbinder() {
            return unbinder;
        }

    }

    /** Callback run at the end of a build. */
    public interface Unbinder extends Serializable {
        /**
         * Performs any needed cleanup.
         * @param build The build. Cannot be null
         * @param workspace The workspace - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
         * @param launcher The launcher - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
         * @param listener The task listener. Cannot be null.
         */
        void unbind(@NonNull Run<?,?> build,
                    @Nullable FilePath workspace,
                    @Nullable Launcher launcher,
                    @NonNull TaskListener listener) throws IOException, InterruptedException;
    }

    /** No-op callback. */
    protected static final class NullUnbinder implements Unbinder {
        private static final long serialVersionUID = 1;
        @Override public void unbind(@NonNull Run<?, ?> build,
                                     @Nullable FilePath workspace,
                                     @Nullable Launcher launcher,
                                     @NonNull TaskListener listener) {}
    }

    /**
     * Sets up bindings for a build.
     * @param build The build. Cannot be null
     * @param workspace The workspace - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
     * @param launcher The launcher - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
     * @param listener The task listener. Cannot be null.
     * @return The configured {@link MultiEnvironment}
     */
    public abstract MultiEnvironment bind(@NonNull Run<?,?> build,
                                          @Nullable FilePath workspace,
                                          @Nullable Launcher launcher,
                                          @NonNull TaskListener listener) throws IOException, InterruptedException;

    /**
     * @deprecated override {@link #variables(Run)}
     */
    public Set<String> variables() {
        return Collections.emptySet();
    }

    /** Defines keys expected to be set in {@link MultiEnvironment#getSecretValues()}, particularly any that might be sensitive. */
    public /*abstract*/ Set<String> variables(@NonNull Run<?,?> build) throws CredentialNotFoundException {
        if (Util.isOverridden(MultiBinding.class, getClass(), "variables")) {
            return variables();
        } else {
            throw new AbstractMethodError("Implement variables");
        }
    }

    /**
     * Looks up the actual credentials.
     * @param build the build.
     * @return the credentials
     * @throws CredentialNotFoundException if the credentials could not be found (for convenience, rather than returning null)
     */
    protected final @NonNull C getCredentials(@NonNull Run<?,?> build) throws CredentialNotFoundException {
        IdCredentials cred = CredentialsProvider.findCredentialById(credentialsId, IdCredentials.class, build);
        if (cred==null)
            throw new CredentialNotFoundException("Could not find credentials entry with ID '" + credentialsId + "'");

        if (type().isInstance(cred)) {
            CredentialsProvider.track(build, cred);
            return type().cast(cred);
        }

        
        Descriptor<?> expected = Jenkins.get().getDescriptor(type());
        throw new CredentialNotFoundException("Credentials '"+credentialsId+"' is of type '"+
                cred.getDescriptor().getDisplayName()+"' where '"+
                (expected!=null ? expected.getDisplayName() : type().getName())+
                "' was expected");
    }

    @Override public BindingDescriptor<C> getDescriptor() {
        return (BindingDescriptor<C>) super.getDescriptor();
    }

}
