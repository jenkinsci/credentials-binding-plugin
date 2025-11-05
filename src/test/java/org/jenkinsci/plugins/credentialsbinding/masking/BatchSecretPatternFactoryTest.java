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

import hudson.Functions;
import hudson.model.Result;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@WithJenkins
class BatchSecretPatternFactoryTest {

    private static final String SIMPLE = "abcABC123";
    private static final String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";
    private static final String ESCAPE = "^<^>^(^)^^^&^|";

    // ALL_ASCII - [ < > " ' ^ $ | % ] 
    private static final String NON_DANGEROUS = "!#$*+,-./ 0123456789:;=? @ABCDEFGHIJKLMNO PQRSTUVWXYZ[\\]_ `abcdefghijklmno pqrstuvwxyz{}~";

    // <>"'^$|
    private static final String NON_DANGEROUS_IN_DOUBLE = "abc<def>$ghi|jkl";

    // quoted form: "^^|\a\\"
    private static final String NEEDS_QUOTING = "^|\\a\\";

    private static final String ALL_ASCII = "!\"#$%&'()*+,-./ 0123456789:;<=>? @ABCDEFGHIJKLMNO PQRSTUVWXYZ[\\]^_ `abcdefghijklmno pqrstuvwxyz{|}~";

    private JenkinsRule r;

    private WorkflowJob project;

    private String credentialPlainText;
    private String credentialId;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        assumeTrue(Functions.isWindows());
    }

    private void registerCredentials(String password) throws IOException {
        this.credentialPlainText = password;
        this.credentialId = CredentialsTestUtil.registerStringCredentials(r.jenkins, password);
    }

    private WorkflowRun runProject() throws Exception {
        return r.assertBuildStatusSuccess(project.scheduleBuild2(0));
    }

    @Test
    void simple_noQuote() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runDirectNoQuote());
    }

    @Test
    void simple_singleQuote() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runDirectSingleQuote());
    }

    @Test
    void simple_doubleQuote() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    void simple_delayed() throws Exception {
        registerCredentials(SIMPLE);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    @Test
    void nonDangerous_noQuote() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDirectNoPlainTextButStars(runDirectNoQuote());
    }

    @Test
    void nonDangerous_singleQuote() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDirectNoPlainTextButStars(runDirectSingleQuote());
    }

    @Test
    void nonDangerous_doubleQuote() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    void nonDangerous_delayed() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    // we do NOT support dangerous characters in direct expansion mode
    @Test
    void allAscii_direct_noQuote() throws Exception {
        registerCredentials(ALL_ASCII);

        WorkflowRun run = runDirectNoQuote();
        r.assertLogNotContains(credentialPlainText, run);

        // EFGHIJK is a part of the credentials that should be masked
        assertStringPresentInOrder(run, "before1", "EFGHIJK", "after1");
        r.assertLogNotContains("before1 **** after1", run);
    }

    // we do NOT support dangerous characters in direct expansion mode
    @Test
    void allAscii_direct_singleQuote() throws Exception {
        registerCredentials(ALL_ASCII);

        WorkflowRun run = runDirectSingleQuote();
        r.assertLogNotContains(credentialPlainText, run);

        // EFGHIJK is a part of the credentials that should be masked
        assertStringPresentInOrder(run, "before1", "EFGHIJK", "after1");
        r.assertLogNotContains("before1 **** after1", run);
    }

    // we do NOT support dangerous characters in direct expansion mode
    @Test
    void allAscii_direct_doubleQuote() throws Exception {
        registerCredentials(ALL_ASCII);
    
        runDirectDoubleQuote_andFail();
    }

    @Test
    void allAscii_delayed() throws Exception {
        registerCredentials(ALL_ASCII);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    // we do NOT support dangerous characters in direct expansion mode
    @Test
    void samplePassword_noQuote() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);

        runDirectNoQuote_andFail();
    }

    // we do NOT support dangerous characters in direct expansion mode
    @Test
    void samplePassword_singleQuote() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);

        runDirectSingleQuote_andFail();
    }

    @Test
    void samplePassword_doubleQuote() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    void samplePassword_delayed() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    @Test
    void escape_noQuote() throws Exception {
        registerCredentials(ESCAPE);
    
        WorkflowRun run = runDirectNoQuote();
        r.assertLogNotContains(credentialPlainText, run);
        r.assertLogContains("before1 **** after1", run);
        r.assertLogContains("before2 **** after2", run);
    }

    @Test
    void escape_singleQuote() throws Exception {
        registerCredentials(ESCAPE);
    
        WorkflowRun run = runDirectSingleQuote();
        r.assertLogNotContains(credentialPlainText, run);
        r.assertLogContains("before1 **** after1", run);
        r.assertLogContains("before2 **** after2", run);
    }

    @Test
    void escape_doubleQuote() throws Exception {
        registerCredentials(ESCAPE);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    void escape_delayed() throws Exception {
        registerCredentials(ESCAPE);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    // special cases
    @Test
    void dangerousOutOfDouble_doubleQuote() throws Exception {
        registerCredentials(NON_DANGEROUS_IN_DOUBLE);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    void needsQuoting_doubleQuote() throws Exception {
        registerCredentials(NEEDS_QUOTING);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    private void assertDirectNoPlainTextButStars(WorkflowRun run) throws Exception {
        r.assertLogNotContains(credentialPlainText, run);
        r.assertLogContains("before1 **** after1", run);
        r.assertLogContains("before2 **** after2", run);
    }

    private void assertDelayedNoPlainTextButStars(WorkflowRun run) throws Exception {
        r.assertLogNotContains(credentialPlainText, run);
        r.assertLogContains("before1 **** after1", run);
        r.assertLogContains("before2 **** after2", run);
        r.assertLogContains("before3 **** after3", run);
    }

    private WorkflowRun runDirectNoQuote() throws Exception {
        setupNoQuoteProject();
        return runProject();
    }

    private void runDirectNoQuote_andFail() throws Exception {
        setupNoQuoteProject();
        r.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
    }

    private void setupNoQuoteProject() throws Exception {
        // DO NOT DO THIS IN PRODUCTION
        // these commands would be completely broken if echo didn't work the way it does when CREDENTIALS contains special characters
        setupProject("node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialId + "', variable: 'CREDENTIALS')]) {\n" +
                "    bat \"\"\"\n" +
                "      echo before1 $CREDENTIALS after1\n" + // DO NOT DO THIS IN PRODUCTION; IT IS USING GROOVY INTERPOLATION
                "    \"\"\"\n" +
                "    bat '''\n" +
                "      echo before2 %CREDENTIALS% after2\n" + // DO NOT DO THIS IN PRODUCTION; IT IS QUOTED WRONG
                "    '''\n" +
                "  }\n" +
                "}"
        );
    }

    private WorkflowRun runDirectSingleQuote() throws Exception {
        setupSingleQuoteProject();
        return runProject();
    }

    private void runDirectSingleQuote_andFail() throws Exception {
        setupSingleQuoteProject();
        r.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
    }

    private void setupSingleQuoteProject() throws Exception {
        // DO NOT DO THIS IN PRODUCTION
        // single quotes do not mean anything in batch scripts; use double quotes for escaping/quoting strings
        setupProject("node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialId + "', variable: 'CREDENTIALS')]) {\n" +
                "    bat \"\"\"\n" +
                "      echo 'before1 $CREDENTIALS after1'\n" + // DO NOT DO THIS IN PRODUCTION; IT IS USING THE WRONG VARIABLE SYNTAX
                "    \"\"\"\n" +
                "    bat '''\n" +
                "      echo 'before2 %CREDENTIALS% after2'\n" + // DO NOT DO THIS IN PRODUCTION; IT IS QUOTED WRONG
                "    '''\n" +
                "  }\n" +
                "}"
        );
    }

    private WorkflowRun runDirectDoubleQuote() throws Exception {
        setupDoubleQuoteProject();
        return runProject();
    }

    private void runDirectDoubleQuote_andFail() throws Exception {
        setupDoubleQuoteProject();
        r.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
    }

    private void setupDoubleQuoteProject() throws Exception {
        setupProject("node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialId + "', variable: 'CREDENTIALS')]) {\n" +
                "    bat \"\"\"\n" +
                "      echo \"before1 $CREDENTIALS after1\"\n" + // DO NOT DO THIS IN PRODUCTION; IT IS USING THE WRONG VARIABLE SYNTAX
                "    \"\"\"\n" +
                "    bat '''\n" +
                "      echo \"before2 %CREDENTIALS% after2\"\n" + // THIS ONE IS OK THOUGH
                "    '''\n" +
                "  }\n" +
                "}"
        );
    }

    private WorkflowRun runDelayedAllQuotes() throws Exception {
        setupProject("node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialId + "', variable: 'CREDENTIALS')]) {\n" +
                "    bat '''\n" +
                "      SETLOCAL EnableDelayedExpansion\n" +
                "      echo before1 !CREDENTIALS! after1\n" +
                "      echo 'before2 !CREDENTIALS! after2'\n" +
                "      echo \"before3 !CREDENTIALS! after3\"\n" +
                "    '''\n" +
                "  }\n" +
                "}"
        );
        return runProject();
    }

    private void assertStringPresentInOrder(WorkflowRun run, String... values) throws Exception {
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

    private void setupProject(String pipeline) throws Exception {
        project = r.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(pipeline, true));
    }
}
