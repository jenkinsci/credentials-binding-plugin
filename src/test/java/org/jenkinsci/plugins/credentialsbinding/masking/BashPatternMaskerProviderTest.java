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

import hudson.tasks.Shell;
import org.jenkinsci.plugins.credentialsbinding.test.CredentialsTestUtil;
import org.jenkinsci.plugins.credentialsbinding.test.Executables;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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
import static org.jenkinsci.plugins.credentialsbinding.test.Executables.executable;
import static org.junit.Assume.assumeThat;

@RunWith(Theories.class)
public class BashPatternMaskerProviderTest {

    public static final @DataPoint String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";
    public static final @DataPoint String ANOTHER_SAMPLE_PASSWORD = "a'b\"c\\d(e)#";
    public static final @DataPoint String ONE_MORE = "'\"'(foo)'\"'";

    @DataPoints
    public static List<String> generatePasswords() {
        Random random = new Random(100);
        List<String> passwords = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            int length = random.nextInt(24) + 8;
            StringBuilder sb = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                // choose a (printable) character in the closed range [' ', '~']
                // 0x7f is DEL, 0x7e is ~, and space is the first printable ASCII character
                char next = (char) (' ' + random.nextInt('\u007f' - ' '));
                sb.append(next);
            }
            passwords.add(sb.toString());
        }
        return passwords;
    }

    @ClassRule public static JenkinsRule j = new JenkinsRule();

    private WorkflowJob project;
    private String credentialsId;

    @BeforeClass
    public static void assumeBash() {
        assumeThat("bash", is(executable()));
        // due to https://github.com/jenkinsci/durable-task-plugin/blob/e75123eda986f20a390d92cc892c3d206e60aefb/src/main/java/org/jenkinsci/plugins/durabletask/BourneShellScript.java#L149
        // on Windows
        assumeThat("nohup", is(executable()));
    }

    @Before
    public void setUp() throws IOException {
        j.jenkins.getDescriptorByType(Shell.DescriptorImpl.class).setShell(Executables.getPathToExecutable("bash"));
        project = j.createProject(WorkflowJob.class);
        credentialsId = UUID.randomUUID().toString();
        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'CREDENTIALS')]) {\n" +
                        "    sh ': \"$CREDENTIALS\"'\n" + // : will expand its parameters and do nothing with them
                        "    sh ': \"< $CREDENTIALS >\"'\n" + // surround credentials with identifiable text for partial quoting
                        "  }\n" +
                        "}", true));
    }

    @Theory
    public void credentialsAreMaskedInLogs(String credentials) throws Exception {
        assumeThat(credentials, not(startsWith("****")));

        CredentialsTestUtil.setStringCredentials(j.jenkins, credentialsId, credentials);
        WorkflowRun run = runProject();

        j.assertLogContains(": ****", run);
        j.assertLogContains(": '< **** >'", run);
        j.assertLogNotContains(credentials, run);
    }

    private WorkflowRun runProject() throws Exception {
        return j.assertBuildStatusSuccess(project.scheduleBuild2(0));
    }

}
