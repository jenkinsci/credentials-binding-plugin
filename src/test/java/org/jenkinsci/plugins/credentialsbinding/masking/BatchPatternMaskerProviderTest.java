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
import hudson.Functions;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assume.assumeTrue;

public class BatchPatternMaskerProviderTest {

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

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private WorkflowJob project;

    private String credentialPlainText;
    private String credentialId;

    @Before
    public void assumeWindowsForBatch() throws Exception {
        assumeTrue(Functions.isWindows());
    }

    private void registerCredentials(String password) throws IOException {
        this.credentialPlainText = password;
        this.credentialId = UUID.randomUUID().toString();
        StringCredentials credentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialId, null, Secret.fromString(password));
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), credentials);
    }

    private WorkflowRun runProject() throws Exception {
        return j.assertBuildStatusSuccess(project.scheduleBuild2(0));
    }

    @Test
    public void simple_noQuote() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runDirectNoQuote());
    }

    @Test
    public void simple_singleQuote() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runDirectSingleQuote());
    }

    @Test
    public void simple_doubleQuote() throws Exception {
        registerCredentials(SIMPLE);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    public void simple_delayed() throws Exception {
        registerCredentials(SIMPLE);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    @Test
    public void nonDangerous_noQuote() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDirectNoPlainTextButStars(runDirectNoQuote());
    }

    @Test
    public void nonDangerous_singleQuote() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDirectNoPlainTextButStars(runDirectSingleQuote());
    }

    @Test
    public void nonDangerous_doubleQuote() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    public void nonDangerous_delayed() throws Exception {
        registerCredentials(NON_DANGEROUS);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    @Test
    @Ignore("Cannot support the dangerous characters in direct expressions")
    public void allAscii_direct() throws Exception {
        registerCredentials(ALL_ASCII);
        assertDirectNoPlainTextButStars(runDirectNoQuote());
        assertDirectNoPlainTextButStars(runDirectSingleQuote());
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    public void allAscii_delayed() throws Exception {
        registerCredentials(ALL_ASCII);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }
    
    @Test
    @Ignore("Cannot support dangerous character & in non-delayed expansion without double quotes")
    public void samplePassword_noQuote() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDirectNoPlainTextButStars(runDirectNoQuote());
    }
    
    @Test
    @Ignore("Cannot support dangerous character & in non-delayed expansion without double quotes")
    public void samplePassword_singleQuote() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDirectNoPlainTextButStars(runDirectSingleQuote());
    }
    
    @Test
    public void samplePassword_doubleQuote() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }
    
    @Test
    public void samplePassword_delayed() throws Exception {
        registerCredentials(SAMPLE_PASSWORD);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    @Test
    public void escape_noQuote() throws Exception {
        registerCredentials(ESCAPE);
    
        WorkflowRun run = runDirectNoQuote();
        j.assertLogNotContains(credentialPlainText, run);
        j.assertLogContains("before1 **** after1", run);
        j.assertLogContains("before2 **** after2", run);
    }

    @Test
    public void escape_singleQuote() throws Exception {
        registerCredentials(ESCAPE);
    
        WorkflowRun run = runDirectSingleQuote();
        j.assertLogNotContains(credentialPlainText, run);
        j.assertLogContains("before1 **** after1", run);
        j.assertLogContains("before2 **** after2", run);
    }

    @Test
    public void escape_doubleQuote() throws Exception {
        registerCredentials(ESCAPE);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    public void escape_delayed() throws Exception {
        registerCredentials(ESCAPE);
        assertDelayedNoPlainTextButStars(runDelayedAllQuotes());
    }

    // special cases
    @Test
    public void dangerousOutOfDouble_doubleQuote() throws Exception {
        registerCredentials(NON_DANGEROUS_IN_DOUBLE);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    @Test
    public void needsQuoting_doubleQuote() throws Exception {
        registerCredentials(NEEDS_QUOTING);
        assertDirectNoPlainTextButStars(runDirectDoubleQuote());
    }

    private void assertDirectNoPlainTextButStars(WorkflowRun run) throws Exception {
        j.assertLogNotContains(credentialPlainText, run);
        j.assertLogContains("before1 **** after1", run);
        j.assertLogContains("before2 **** after2", run);
    }

    private void assertDelayedNoPlainTextButStars(WorkflowRun run) throws Exception {
        j.assertLogNotContains(credentialPlainText, run);
        j.assertLogContains("before1 **** after1", run);
        j.assertLogContains("before2 **** after2", run);
        j.assertLogContains("before3 **** after3", run);
    }

    private WorkflowRun runDirectNoQuote() throws Exception {
        // DO NOT DO THIS IN PRODUCTION
        // these commands would be completely broken if echo didn't work the way it does when CREDENTIALS contains special characters
        setupProject("node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialId + "', variable: 'CREDENTIALS')]) {\n" +
                "    bat \"\"\"\n" +
                "      echo before1 $CREDENTIALS after1\n" + // DO NOT DO THIS IN PRODUCTION; IT IS USING THE WRONG VARIABLE SYNTAX
                "    \"\"\"\n" +
                "    bat '''\n" +
                "      echo before2 %CREDENTIALS% after2\n" + // DO NOT DO THIS IN PRODUCTION; IT IS QUOTED WRONG
                "    '''\n" +
                "  }\n" +
                "}"
        );
        return runProject();
    }

    private WorkflowRun runDirectSingleQuote() throws Exception {
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
        return runProject();
    }

    private WorkflowRun runDirectDoubleQuote() throws Exception {
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
        return runProject();
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

    private void setupProject(String pipeline) throws Exception {
        String projectName = UUID.randomUUID().toString();
        project = j.jenkins.createProject(WorkflowJob.class, projectName);
        credentialId = UUID.randomUUID().toString();
        project.setDefinition(new CpsFlowDefinition(pipeline, true));
    }
}
