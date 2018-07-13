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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
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

        @Deprecated
        private transient Map<String,String> values;
        private Map<String,String> secretValues;
        private Map<String,String> publicValues;
        private final Unbinder unbinder;

        public MultiEnvironment(Map<String,String> secretValues) {
            this(secretValues, Collections.<String, String>emptyMap());
        }

        public MultiEnvironment(Map<String,String> secretValues, Map<String,String> publicValues) {
            this(secretValues, publicValues, new NullUnbinder());
        }

        public MultiEnvironment(Map<String,String> secretValues, Unbinder unbinder) {
            this(secretValues, Collections.<String, String>emptyMap(), unbinder);
        }

        public MultiEnvironment(Map<String,String> secretValues, Map<String,String> publicValues, Unbinder unbinder) {
            this.values = null;
            this.secretValues = new HashMap<>(secretValues);
            this.publicValues = new HashMap<>(publicValues);
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
        void unbind(@Nonnull Run<?,?> build,
                    @Nullable FilePath workspace,
                    @Nullable Launcher launcher,
                    @Nonnull TaskListener listener) throws IOException, InterruptedException;
    }

    /** No-op callback. */
    protected static final class NullUnbinder implements Unbinder {
        private static final long serialVersionUID = 1;
        @Override public void unbind(@Nonnull Run<?, ?> build,
                                     @Nullable FilePath workspace,
                                     @Nullable Launcher launcher,
                                     @Nonnull TaskListener listener) throws IOException, InterruptedException {}
    }

    /**
     * Sets up bindings for a build.
     * @param build The build. Cannot be null
     * @param workspace The workspace - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
     * @param launcher The launcher - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
     * @param listener The task listener. Cannot be null.
     * @return The configured {@link MultiEnvironment}
     */
    public abstract MultiEnvironment bind(@Nonnull Run<?,?> build,
                                          @Nullable FilePath workspace,
                                          @Nullable Launcher launcher,
                                          @Nonnull TaskListener listener) throws IOException, InterruptedException;

    /** Defines keys expected to be set in {@link MultiEnvironment#getSecretValues()}, particularly any that might be sensitive. */
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
            throw new CredentialNotFoundException("Could not find credentials entry with ID '" + credentialsId + "'");

        if (type().isInstance(cred)) {
            CredentialsProvider.track(build, cred);
            return type().cast(cred);
        }

        
        Descriptor expected = Jenkins.getActiveInstance().getDescriptor(type());
        throw new CredentialNotFoundException("Credentials '"+credentialsId+"' is of type '"+
                cred.getDescriptor().getDisplayName()+"' where '"+
                (expected!=null ? expected.getDisplayName() : type().getName())+
                "' was expected");
    }

    @Override public BindingDescriptor<C> getDescriptor() {
        return (BindingDescriptor<C>) super.getDescriptor();
    }

    private static final Comparator<String> stringLengthComparator = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            return o2.length() - o1.length();
        }
    };

    /**
     * Utility method for turning a collection of secret strings into a single {@link String} for pattern compilation.
     * @param secrets A collection of secret strings
     * @return A {@link String} generated from that collection.
     */
    @Restricted(NoExternalUse.class)
    public static String getPatternStringForSecrets(Collection<String> secrets) {
        StringBuilder b = new StringBuilder();
        List<String> sortedByLength = new ArrayList<String>(secrets);
        Collections.sort(sortedByLength, stringLengthComparator);

        for (String secret : sortedByLength) {
            if (!secret.isEmpty()) {
                if (b.length() > 0) {
                    b.append('|');
                }
                b.append(Pattern.quote(secret));
            }
        }
        return b.toString();
    }
}
