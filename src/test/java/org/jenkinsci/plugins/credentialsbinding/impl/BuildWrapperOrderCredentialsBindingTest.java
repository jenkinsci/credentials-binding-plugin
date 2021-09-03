/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.EnvVars;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Shell;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BuildWrapperOrderCredentialsBindingTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    static String credentialsId = "creds_1";
    static String password = "p4ss";
    static String bindingKey = "PASS_1";

    @Issue("JENKINS-37871")
    @Test public void secretBuildWrapperRunsBeforeNormalWrapper() throws Exception {
        StringCredentialsImpl firstCreds = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample1", Secret.fromString(password));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), firstCreds);

        SecretBuildWrapper wrapper = new SecretBuildWrapper(Collections.singletonList(new StringBinding(bindingKey, credentialsId)));

        FreeStyleProject f = r.createFreeStyleProject("buildWrapperOrder");

        f.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %PASS_1%") : new Shell("echo $PASS_1"));
        f.getBuildWrappersList().add(new BuildWrapperOrder());
        f.getBuildWrappersList().add(wrapper);

        // configRoundtrip makes sure the ordinal of SecretBuildWrapper extension is applied correctly.
        r.configRoundtrip(f);

        FreeStyleBuild b = r.buildAndAssertSuccess(f);
        r.assertLogContains("Secret found!", b);
    }

    public static class BuildWrapperOrder extends BuildWrapper {

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            EnvVars env = build.getEnvironment(listener);

            // Lookup secret provided by SecretBuildWrapper.
            // This only works if this BuildWrapper is executed AFTER the SecretBuildWrapper so the binding is already done.
            for (Map.Entry<String, String> entry : env.entrySet()) {
                if (entry.getKey().equals(bindingKey) && entry.getValue().equals(password)) {
                    listener.getLogger().format("Secret found!");
                    break;
                }
            }

            return new Environment() {};
        }

        @TestExtension
        public static class BuildWrapperOrderDescriptor extends BuildWrapperDescriptor {

            public BuildWrapperOrderDescriptor() {
                super(BuildWrapperOrder.class);
            }

            @Override
            public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }

            @Override
            public BuildWrapper newInstance(StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
                return new BuildWrapperOrder();
            }

        }
    }

}
