/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package org.jenkinsci.plugins.credentialsbinding.masking;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.jenkinsci.plugins.credentialsbinding.test.Executables;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.credentialsbinding.test.Executables.executable;
import static org.junit.Assume.assumeThat;

public class BashPatternMaskerProvider2Test {

    public @Rule JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        assumeThat("bash", is(executable()));
        j.jenkins.getDescriptorByType(Shell.DescriptorImpl.class).setShell(Executables.getPathToExecutable("bash"));
    }

    @Test
    // DO NOT DO THIS IN PRODUCTION; IT IS QUOTED WRONG
    public void testSecretsWithBackslashesStillMaskedWhenUsedWithoutProperQuoting() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        String password = "foo\\bar\\";
        String credentialsId = registerStringCredentials(password);
        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'CREDENTIALS')]) {\n" +
                        "    sh ': $CREDENTIALS'\n" + // forgot quotes
                        "    sh \": $CREDENTIALS\"\n" + // using groovy variable and forgot quotes
                        "  }\n" +
                        "}", true));

        WorkflowRun run = j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        j.assertLogContains(": ****", run);
        j.assertLogNotContains(password, run);
        j.assertLogNotContains("foo", run);
        j.assertLogNotContains("bar", run);
    }

    private String registerStringCredentials(String password) throws IOException {
        String credentialId = UUID.randomUUID().toString();
        StringCredentials creds = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialId, null, Secret.fromString(password));
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), creds);
        return credentialId;
    }
}
