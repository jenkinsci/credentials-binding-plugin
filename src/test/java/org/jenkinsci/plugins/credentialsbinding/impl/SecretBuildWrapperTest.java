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
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

public class SecretBuildWrapperTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-24805")
    @Test public void maskingFreeStyleSecrets() throws Exception {
        String credentialsId_1 = "creds_1";
        String username_1 = "s3cr3t";
        String password_1 = "p4ss";
        StringCredentialsImpl c_1 = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId_1, "sample1", Secret.fromString(password_1));
        String credentialsId_2 = "creds_2";
        String username_2 = "s3cr3t0";
        String password_2 = "p4ss" + "EvenLonger";
        StringCredentialsImpl c_2 = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId_2, "sample2", Secret.fromString(password_2));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c_1);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c_2);

        SecretBuildWrapper bw_1 = new SecretBuildWrapper(Collections.<MultiBinding<?>>singletonList(new StringBinding("PASS_1", credentialsId_1)));
        SecretBuildWrapper bw_2 = new SecretBuildWrapper(Collections.<MultiBinding<?>>singletonList(new StringBinding("PASS_2", credentialsId_2)));

        FreeStyleProject f = r.createFreeStyleProject();

        f.setConcurrentBuild(true);
        f.getBuildersList().add(new Shell("echo $PASS_1"));
        f.getBuildersList().add(new Shell("echo $PASS_2"));
        f.getBuildWrappersList().add(bw_1);
        f.getBuildWrappersList().add(bw_2);

        r.configRoundtrip((Item)f);

        FreeStyleBuild b = r.buildAndAssertSuccess(f);
        r.assertLogNotContains(password_1, b);
        r.assertLogNotContains(password_2, b);
        r.assertLogContains("echo ****", b);
    }

}
