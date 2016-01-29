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
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

@SuppressWarnings({"rawtypes", "unchecked"}) // inherited from BuildWrapper
public class SecretBuildWrapper extends BuildWrapper {

    private final List<? extends MultiBinding<?>> bindings;

    @DataBoundConstructor public SecretBuildWrapper(List<? extends MultiBinding<?>> bindings) {
        this.bindings = bindings;
    }

    public List<? extends MultiBinding<?>> getBindings() {
        return bindings;
    }

    @Override public Environment setUp(final AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {};
    }

    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream outputStream) throws IOException, InterruptedException, Run.RunnerAbortedException {
        List<String> passwords = new ArrayList<String>();
        for (MultiBinding binding : bindings) {
            List<String> environements = new ArrayList<String>();
            environements.addAll(binding.variables());
            
            for (Map.Entry<String, String> entry : binding.bind(build, build.getWorkspace()).getValues().entrySet()) {
                if (environements.contains(entry.getKey())) {
                    passwords.add(entry.getValue());
                }
            }
        }

        return new CredentialsBindingPasswordsOutputStream(outputStream, passwords);
    }

     /**
     * Class took from the mask-passwords plugin / envinject plugin
     */
    class CredentialsBindingPasswordsOutputStream extends LineTransformationOutputStream {

        private final OutputStream logger;
        private final Pattern passwordsAsPattern;

        CredentialsBindingPasswordsOutputStream(OutputStream logger, Collection<String> passwords) {

            this.logger = logger;

            if (passwords != null && passwords.size() > 0) {
                // passwords are aggregated into a regex which is compiled as a pattern
                // for efficiency
                StringBuilder regex = new StringBuilder().append('(');

                int nbMaskedPasswords = 0;
                for (String password : passwords) {
                    if (StringUtils.isNotEmpty(password)) { // we must not handle empty passwords
                        regex.append(Pattern.quote(password));
                        regex.append('|');
                        nbMaskedPasswords++;
                    }
                }
                if (nbMaskedPasswords >= 1) { // is there at least one password to mask?
                    regex.deleteCharAt(regex.length() - 1); // removes the last unuseful pipe
                    regex.append(')');
                    passwordsAsPattern = Pattern.compile(regex.toString());
                } else { // no passwords to hide
                    passwordsAsPattern = null;
                }
            } else { // no passwords to hide
                passwordsAsPattern = null;
            }
        }

        @Override
        protected void eol(byte[] bytes, int len) throws IOException {
            String line = new String(bytes, 0, len);
            if (passwordsAsPattern != null) {
                line = passwordsAsPattern.matcher(line).replaceAll("********");
            }
            logger.write(line.getBytes());
        }

        @Override
        public void close() throws IOException {
            super.close();
            logger.close();
        }       
    }

    @Override public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
        final List<MultiBinding.MultiEnvironment> multi = new ArrayList<MultiBinding.MultiEnvironment>();
        for (MultiBinding binding : bindings) {
            try {
                multi.add(binding.bind(build, build.getWorkspace()));
            } catch (IOException ex) {
                Logger.getLogger(SecretBuildWrapper.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(SecretBuildWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        for (MultiBinding.MultiEnvironment envs : multi) {
            variables.putAll(envs.getValues());
        }
    }

    @Override public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        for (MultiBinding binding : bindings) {
            sensitiveVariables.addAll(binding.variables());
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
