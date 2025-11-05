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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.jenkinsci.plugins.credentialsbinding.test.Executables.isExecutable;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@WithJenkins
class AlmquistShellSecretPatternFactoryTest {

    // ' is escaped as '"'"', '' as '"''"', ''' as '"'''"'
    private static final String MULTIPLE_QUOTES = "ab'cd''ef'''gh";
    // "starting" and "ending" single quotes are escaped as "middle" single quotes
    private static final String SURROUNDED_BY_QUOTES = "'abc'";
    private static final String SURROUNDED_BY_QUOTES_AND_MIDDLE = "'ab'cd'";
    private static final String SIMPLE_CASE_1 = "abc";
    private static final String SIMPLE_CASE_2 = "ab'cd";
    private static final String SIMPLE_CASE_3 = "ab''cd";
    private static final String SIMPLE_CASE_4 = "ab'c'd";
    private static final String LEADING_QUOTE = "'a\"b\"c d";
    private static final String TRAILING_QUOTE = "a\"b\"c d'";
    private static final String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";
    private static final String ANOTHER_SAMPLE_PASSWORD = "a'b\"c\\d(e)#";
    private static final String ONE_MORE = "'\"'(foo)'\"'";
    private static final String FULL_ASCII = "!\"#$%&'()*+,-./ 0123456789:;<=>? @ABCDEFGHIJKLMNO PQRSTUVWXYZ[\\]^_ `abcdefghijklmno pqrstuvwxyz{|}~";

    static List<String> generatePasswords() {
        Random random = new Random(100);
        List<String> passwords = new ArrayList<>();

        passwords.add(MULTIPLE_QUOTES);
        passwords.add(SURROUNDED_BY_QUOTES);
        passwords.add(SURROUNDED_BY_QUOTES_AND_MIDDLE);
        passwords.add(SIMPLE_CASE_1);
        passwords.add(SIMPLE_CASE_2);
        passwords.add(SIMPLE_CASE_3);
        passwords.add(SIMPLE_CASE_4);
        passwords.add(LEADING_QUOTE);
        passwords.add(TRAILING_QUOTE);
        passwords.add(SAMPLE_PASSWORD);
        passwords.add(ANOTHER_SAMPLE_PASSWORD);
        passwords.add(ONE_MORE);
        passwords.add(FULL_ASCII);

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

    private static JenkinsRule r;

    private WorkflowJob project;
    private String credentialsId;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        r = rule;
        // ash = Almquist shell, default one used in Alpine
        assumeTrue(isExecutable("ash"));
        // due to https://github.com/jenkinsci/durable-task-plugin/blob/e75123eda986f20a390d92cc892c3d206e60aefb/src/main/java/org/jenkinsci/plugins/durabletask/BourneShellScript.java#L149
        // on Windows
        assumeTrue(isExecutable("nohup"));
    }

    @BeforeEach
    void beforeEach() throws Exception {
        r.jenkins.getDescriptorByType(Shell.DescriptorImpl.class).setShell(Executables.getPathToExecutable("ash"));
        project = r.createProject(WorkflowJob.class);
        credentialsId = UUID.randomUUID().toString();

        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'CREDENTIALS')]) {\n" +
                        "    sh '''\n" +
                        "       echo begin0 $CREDENTIALS end0\n" +
                        // begin2 => 2 for double quotes
                        "       echo \"begin2 $CREDENTIALS end2\"\n" +
                        "    '''\n" +
                        "  }\n" +
                        "}", true));
    }

    @ParameterizedTest
    @MethodSource("generatePasswords")
    void credentialsAreMaskedInLogs(String credentials) throws Exception {
        assumeFalse(credentials.startsWith("****"));

        CredentialsTestUtil.setStringCredentials(r.jenkins, credentialsId, credentials);
        WorkflowRun run = runProject();

        r.assertLogContains("begin0 **** end0", run);
        r.assertLogContains("begin2 **** end2", run);
        r.assertLogNotContains(credentials, run);
    }

    private WorkflowRun runProject() throws Exception {
        return r.buildAndAssertSuccess(project);
    }

}
