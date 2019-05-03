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
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.jenkinsci.plugins.credentialsbinding.test.ExecutableExists.executable;
import static org.junit.Assume.assumeThat;

@RunWith(Theories.class)
public class BashPatternMaskerProviderTest {

    public static final @DataPoint String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";
    public static final @DataPoint String ANOTHER_SAMPLE_PASSWORD = "a'b\"c\\d(e)#";

    @DataPoints
    public static List<String> generatePasswords() {
        Random random = new Random(100);
        List<String> passwords = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            int length = random.nextInt(24) + 8;
            StringBuilder sb = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                char next = (char) (random.nextInt('~' - ' ' + 1) + ' '); // space = 0x20, tilde = 0x7E
                sb.append(next);
            }
            passwords.add(sb.toString());
        }
        return passwords;
    }

    @Rule public JenkinsRule j = new JenkinsRule();

    private WorkflowJob project;
    private String credentialsId;

    @Before
    public void setUp() throws IOException {
        assumeThat("bash", is(executable()));
        project = j.createProject(WorkflowJob.class);
        credentialsId = UUID.randomUUID().toString();
        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'CREDENTIALS')]) {\n" +
                        "    sh ': \"$CREDENTIALS\"'\n" + // : will expand its parameters and do nothing with them
                        "    sh ': \"< $CREDENTIALS >\"'\n" +
                        "  }\n" +
                        "}", true));
    }

    @Theory
    public void credentialsAreMaskedInLogs(String credentials) throws Exception {
        assumeThat(credentials, not(startsWith("****")));

        registerCredentials(credentials);
        WorkflowRun run = runProject();

        j.assertLogContains(": ****", run);
        j.assertLogContains(": '< **** >'", run);
        j.assertLogNotContains(credentials, run);
    }

    private void registerCredentials(String password) throws IOException {
        StringCredentials credentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, null, Secret.fromString(password));
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), credentials);
    }

    private WorkflowRun runProject() throws Exception {
        return j.assertBuildStatusSuccess(project.scheduleBuild2(0));
    }

}
