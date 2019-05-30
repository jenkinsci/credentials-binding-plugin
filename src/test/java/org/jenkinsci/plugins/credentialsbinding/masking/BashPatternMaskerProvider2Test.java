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
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.credentialsbinding.test.Executables.executable;
import static org.junit.Assume.assumeThat;

@For(BashPatternMaskerProvider.class)
public class BashPatternMaskerProvider2Test {

    public @Rule JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        assumeThat("bash", is(executable()));
        // due to https://github.com/jenkinsci/durable-task-plugin/blob/e75123eda986f20a390d92cc892c3d206e60aefb/src/main/java/org/jenkinsci/plugins/durabletask/BourneShellScript.java#L149
        // on Windows
        assumeThat("nohup", is(executable()));
        j.jenkins.getDescriptorByType(Shell.DescriptorImpl.class).setShell(Executables.getPathToExecutable("bash"));
    }

    @Test
    // DO NOT DO THIS IN PRODUCTION; IT IS QUOTED WRONG
    public void testSecretsWithBackslashesStillMaskedWhenUsedWithoutProperQuoting() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        String password = "foo\\bar\\";
        String credentialsId = CredentialsTestUtil.registerStringCredentials(j.jenkins, password);
        project.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'CREDENTIALS')]) {\n" +
                        "    sh ': $CREDENTIALS'\n" + // forgot quotes
                        "    sh(/: $CREDENTIALS/)\n" + // using Groovy variable and forgot quotes
                        "  }\n" +
                        "}", true));

        WorkflowRun run = j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        j.assertLogContains(": ****", run);
        j.assertLogNotContains(password, run);
        j.assertLogNotContains("foo", run);
        j.assertLogNotContains("bar", run);
    }
}
