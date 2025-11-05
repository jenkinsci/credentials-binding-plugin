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

import org.jenkinsci.plugins.credentialsbinding.test.CredentialsTestUtil;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.plugins.credentialsbinding.test.Executables.isExecutable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@WithJenkins
class PowerShellMaskerProviderTest {

    private static final String SIMPLE = "abcABC123";
    private static final String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";
    private static final String ALL_ASCII = "!\"#$%&'()*+,-./ 0123456789:;<=>? @ABCDEFGHIJKLMNO PQRSTUVWXYZ[\\]^_ `abcdefghijklmno pqrstuvwxyz{|}~";

    private JenkinsRule r;

    private WorkflowJob project;

    private String credentialPlainText;
    private String credentialId;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        // TODO: pwsh is also a valid executable name
        // https://github.com/jenkinsci/durable-task-plugin/pull/88
        assumeTrue(isExecutable("powershell"));
    }

    private void registerCredentials(String password) throws IOException {
        this.credentialPlainText = password;
        this.credentialId = CredentialsTestUtil.registerStringCredentials(r.jenkins, password);
    }

    @Test
    void simple() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runPowerShellInterpretation());
    }

    @Test
    void allAscii() throws Exception {
        registerCredentials(ALL_ASCII);
        assertDirectNoPlainTextButStars(runPowerShellInterpretation());
    }

    @Test
    void samplePassword() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDirectNoPlainTextButStars(runPowerShellInterpretation());
    }

    private void assertDirectNoPlainTextButStars(WorkflowRun run) throws Exception {
        r.assertLogNotContains(credentialPlainText, run);
        // powershell x y z => output in 3 different lines
        assertStringPresentInOrder(run, "before1", "****", "after1");
        r.assertLogContains("before2 **** after2", run);
    }
    
    private static void assertStringPresentInOrder(WorkflowRun run, String... values) throws Exception {
        String fullLog = run.getLog();
        int currentIndex = 0;
        for (String currentValue : values) {
            int nextIndex = fullLog.indexOf(currentValue, currentIndex);
            if (nextIndex == -1) {
                // use assertThat to have better output
                assertThat(fullLog.substring(currentIndex), containsString(currentValue));
            } else {
                currentIndex = nextIndex + currentValue.length();
            }
        }
    }

    private WorkflowRun runPowerShellInterpretation() throws Exception {
        setupProject("node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialId + "', variable: 'CREDENTIALS')]) {\n" +
                // interpreted by PowerShell
                "    powershell '''\n" +
                // echo is an alias for Write-Output
                "      echo before1 $env:CREDENTIALS after1\n" +
                // echo '...' does not let PowerShell to interpret the information
                "      echo \"before2 $env:CREDENTIALS after2\"\n" +
                "    '''\n" +
                "  }\n" +
                "}"
        );
        return r.assertBuildStatusSuccess(project.scheduleBuild2(0));
    }

    private void setupProject(String pipeline) throws Exception {
        project = r.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(pipeline, true));
    }
}
