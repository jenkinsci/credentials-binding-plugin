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

import com.cloudbees.plugins.credentials.common.StandardCredentials;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A binding of a single variable.
 * @param <C> a kind of credentials
 */
public abstract class Binding<C extends StandardCredentials> extends MultiBinding<C> {

    private final String variable;

    /** For use with {@link DataBoundConstructor}. */
    protected Binding(String variable, String credentialsId) {
        super(credentialsId);
        this.variable = variable;
    }

    /** Environment variable name. */
    public String getVariable() {
        return variable;
    }

    /** Result of {@link #bindSingle}. */
    @SuppressWarnings("PublicConstructorInNonPublicClass") // intended to be called from a subclass of Binding
    protected static final class SingleEnvironment {

        final String value;
        final Unbinder unbinder;

        public SingleEnvironment(String value) {
            this(value, new NullUnbinder());
        }

        public SingleEnvironment(String value, Unbinder unbinder) {
            this.value = value;
            this.unbinder = unbinder;
        }

    }

    @Deprecated
    public interface Environment {

        /** Produces the value of the environment variable. */
        String value();

        /** Performs any needed cleanup. */
        void unbind() throws IOException, InterruptedException;

    }

    @Deprecated
    @SuppressWarnings("rawtypes")
    public Environment bind(@Nonnull final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        final SingleEnvironment e = bindSingle(build, build.getWorkspace(), launcher, listener);
        return new Environment() {
            @Override public String value() {
                return e.value;
            }
            @Override public void unbind() throws IOException, InterruptedException {
                e.unbinder.unbind(build, build.getWorkspace(), launcher, listener);
            }
        };
    }

    /**
     * Sets up bindings for a build.
     * @param build The build. Cannot be null
     * @param workspace The workspace - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
     * @param launcher The launcher - can be null if {@link BindingDescriptor#requiresWorkspace()} is false.
     * @param listener The task listener. Cannot be null.
     * @return The configured {@link SingleEnvironment}
     */
    public /* abstract */SingleEnvironment bindSingle(@Nonnull Run<?,?> build,
                                                      @Nullable FilePath workspace,
                                                      @Nullable Launcher launcher,
                                                      @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (Util.isOverridden(Binding.class, getClass(), "bind", AbstractBuild.class, Launcher.class, BuildListener.class) && build instanceof AbstractBuild && listener instanceof BuildListener) {
            Environment e = bind((AbstractBuild) build, launcher, (BuildListener) listener);
            return new SingleEnvironment(e.value(), new UnbinderWrapper(e));
        } else {
            throw new AbstractMethodError("you must override bindSingle");
        }
    }
    
    private static class UnbinderWrapper implements Unbinder {
        private static final long serialVersionUID = 1; // only really serialize if what it wraps is, too
        
        @SuppressFBWarnings(value="SE_BAD_FIELD", justification="Environment is deprecated and will generally be not serializable")
        private final Environment e;
        
        UnbinderWrapper(Environment e) {
            this.e = e;
        }
        @Override public void unbind(@Nonnull Run<?, ?> build,
                                     @Nullable FilePath workspace,
                                     @Nullable Launcher launcher,
                                     @Nonnull TaskListener listener) throws IOException, InterruptedException {
            e.unbind();
        }
    }


    @Override public final MultiEnvironment bind(@Nonnull Run<?,?> build,
                                                 @Nullable FilePath workspace,
                                                 @Nullable Launcher launcher,
                                                 @Nonnull TaskListener listener) throws IOException, InterruptedException {
        SingleEnvironment single = bindSingle(build, workspace, launcher, listener);
        return new MultiEnvironment(Collections.singletonMap(variable, single.value), single.unbinder);
    }

    @Override public final Set<String> variables(Run<?, ?> build) {
        return Collections.singleton(variable);
    }

    @Deprecated
    protected final @Nonnull C getCredentials(@Nonnull AbstractBuild<?,?> build) throws IOException {
        return super.getCredentials(build);
    }

}
