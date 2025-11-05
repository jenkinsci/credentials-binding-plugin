/*
 * The MIT License
 *
 * Copyright 2016 CloudBees inc.
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
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SecretBuildWrapperTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-24805")
    @Test
    void maskingFreeStyleSecrets() throws Exception {
        String firstCredentialsId = "creds_1";
        String firstPassword = "p4$$";
        StringCredentialsImpl firstCreds = new StringCredentialsImpl(CredentialsScope.GLOBAL, firstCredentialsId, "sample1", Secret.fromString(firstPassword));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), firstCreds);

        String secondCredentialsId = "creds_2";
        String secondPassword = "p4$$" + "someMoreStuff";
        StringCredentialsImpl secondCreds = new StringCredentialsImpl(CredentialsScope.GLOBAL, secondCredentialsId, "sample2", Secret.fromString(secondPassword));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), secondCreds);

        SecretBuildWrapper wrapper = new SecretBuildWrapper(Arrays.asList(new StringBinding("PASS_1", firstCredentialsId),
                new StringBinding("PASS_2", secondCredentialsId)));

        FreeStyleProject f = r.createFreeStyleProject();

        f.setConcurrentBuild(true);
        f.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %PASS_1%") : new Shell("echo \"$PASS_1\""));
        f.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %PASS_2%") : new Shell("echo \"$PASS_2\""));
        f.getBuildWrappersList().add(wrapper);

        r.configRoundtrip((Item)f);

        FreeStyleBuild b = r.buildAndAssertSuccess(f);
        r.assertLogNotContains(firstPassword, b);
        r.assertLogNotContains(secondPassword, b);
        r.assertLogContains("****", b);
    }

    @Issue("JENKINS-24805")
    @Test
    void emptySecretsList() throws Exception {
        SecretBuildWrapper wrapper = new SecretBuildWrapper(new ArrayList<>());

        FreeStyleProject f = r.createFreeStyleProject();

        f.setConcurrentBuild(true);
        f.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo PASSES") : new Shell("echo PASSES"));
        f.getBuildWrappersList().add(wrapper);

        r.configRoundtrip((Item)f);

        FreeStyleBuild b = r.buildAndAssertSuccess(f);
        r.assertLogContains("PASSES", b);
    }

    @Issue("JENKINS-41760")
    @Test
    void emptySecret() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL, "creds", null, Secret.fromString("")));
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new SecretBuildWrapper(Collections.singletonList(new StringBinding("SECRET", "creds"))));
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo PASSES") : new Shell("echo PASSES"));
        r.assertLogContains("PASSES", r.buildAndAssertSuccess(p));
    }

    @Issue("SECURITY-1374")
    @Test
    void maskingPostBuild() throws Exception {
        String credentialsId = "creds_1";
        String password = "p4$$";
        StringCredentialsImpl firstCreds = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample1", Secret.fromString(password));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), firstCreds);

        SecretBuildWrapper wrapper = new SecretBuildWrapper(Collections.singletonList(new StringBinding("PASS_1", credentialsId)));

        FreeStyleProject f = r.createFreeStyleProject();

        f.setConcurrentBuild(true);
        f.getBuildWrappersList().add(wrapper);
        Publisher publisher = new PasswordPublisher(password);
        f.getPublishersList().add(publisher);

        FreeStyleBuild b = r.buildAndAssertSuccess(f);
        r.assertLogNotContains(password, b);
        r.assertLogContains("****", b);
    }

    static class PasswordPublisher extends Recorder {

        private final String password;

        public PasswordPublisher(String password) {
            this.password = password;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            listener.getLogger().println("Sneak it in during the postbuild: " + password + " :done.");
            return true;
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

    }

}
