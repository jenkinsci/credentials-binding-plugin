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
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.util.Secret;
import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings({"rawtypes", "unchecked"}) // inherited from BuildWrapper
public class SecretBuildWrapper extends BuildWrapper {

    private /*almost final*/ List<? extends MultiBinding<?>> bindings;

    private static Map<AbstractBuild<?, ?>, Collection<String>> secretsForBuild = new WeakHashMap<AbstractBuild<?, ?>, Collection<String>>();

    /**
     * Gets the {@link Pattern} for the secret values for a given build, if that build has secrets defined. If not, return
     * null.
     * @param build A non-null build.
     * @return A compiled {@link Pattern} from the build's secret values, if the build has any.
     */
    public static @Nullable Pattern getPatternForBuild(@Nonnull AbstractBuild<?, ?> build) {
        if (secretsForBuild.containsKey(build)) {
            return Pattern.compile(MultiBinding.getSecretForStrings(secretsForBuild.get(build)).getPlainText());
        } else {
            return null;
        }
    }

    @DataBoundConstructor public SecretBuildWrapper(List<? extends MultiBinding<?>> bindings) {
        this.bindings = bindings == null ? Collections.<MultiBinding<?>>emptyList() : bindings;
    }

    public List<? extends MultiBinding<?>> getBindings() {
        return bindings;
    }

    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException, Run.RunnerAbortedException {
        if (!bindings.isEmpty()) {
            return new Filter(build.getCharset().name()).decorateLogger(build, logger);
        } else {
            return logger;
        }
    }

    @Override public Environment setUp(final AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final List<MultiBinding.MultiEnvironment> m = new ArrayList<MultiBinding.MultiEnvironment>();
        for (MultiBinding binding : bindings) {
            m.add(binding.bind(build, build.getWorkspace(), launcher, listener));
        }

        secretsForBuild.put(build, new HashSet<String>());

        return new Environment() {
            @Override public void buildEnvVars(Map<String,String> env) {
                for (MultiBinding.MultiEnvironment e : m) {
                    env.putAll(e.getValues());
                    secretsForBuild.get(build).addAll(e.getValues().values());
                }
            }
            @Override public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                for (MultiBinding.MultiEnvironment e : m) {
                    e.getUnbinder().unbind(build, build.getWorkspace(), launcher, listener);
                }
                secretsForBuild.remove(build);
                return true;
            }
        };
    }

    @Override public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        for (MultiBinding binding : bindings) {
            sensitiveVariables.addAll(binding.variables());
        }
    }

    protected Object readResolve() {
        if (bindings == null) {
            bindings = Collections.emptyList();
        }
        return this;
    }

    /** Similar to {@code MaskPasswordsOutputStream}. */
    private static final class Filter extends ConsoleLogFilter implements Serializable {

        private static final long serialVersionUID = 1;

        private String charsetName;

        Filter(String charsetName) {
            this.charsetName = charsetName;
        }

        // To avoid de-serialization issues with newly added field (charsetName)
        private Object readResolve() throws ObjectStreamException {
            if (this.charsetName == null) {
                this.charsetName = Charsets.UTF_8.name();
            }
            return this;
        }

        @Override public OutputStream decorateLogger(final AbstractBuild build, final OutputStream logger) throws IOException, InterruptedException {
            return new LineTransformationOutputStream() {
                Pattern p;

                @Override protected void eol(byte[] b, int len) throws IOException {
                    if (p == null) {
                        p = getPatternForBuild(build);
                    }

                    if (p != null) {
                        Matcher m = p.matcher(new String(b, 0, len, charsetName));
                        if (m.find()) {
                            logger.write(m.replaceAll("****").getBytes(charsetName));
                        } else {
                            // Avoid byte → char → byte conversion unless we are actually doing something.
                            logger.write(b, 0, len);
                        }
                    } else {
                        // Avoid byte → char → byte conversion unless we are actually doing something.
                        logger.write(b, 0, len);
                    }
                }
            };
        }

    }

    @Extension public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override public String getDisplayName() {
            return Messages.SecretBuildWrapper_use_secret_text_s_or_file_s_();
        }

    }

}
