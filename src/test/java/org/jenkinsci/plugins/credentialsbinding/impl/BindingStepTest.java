/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.FilePath;
import hudson.model.FileParameterValue;
import java.io.File;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class BindingStepTest {

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void configRoundTrip() throws Exception {
        story.addStep(new Statement() {
            @SuppressWarnings("rawtypes")
            @Override public void evaluate() throws Throwable {
                UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", "bob", "s3cr3t");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                BindingStep s = new StepConfigTester(story.j).configRoundTrip(new BindingStep(Collections.<MultiBinding>singletonList(new UsernamePasswordBinding("userpass", "creds"))));
                story.j.assertEqualDataBoundBeans(s.getBindings(), Collections.singletonList(new UsernamePasswordBinding("userpass", "creds")));
            }
        });
    }

    @Test public void basics() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", "bob", "s3cr3t");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  withCredentials([[$class: 'UsernamePasswordMultiBinding', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD', credentialsId: 'creds']]) {\n"
                        + "    sh '''\n"
                        + "      set +x\n"
                        + "      echo curl -u $USERNAME:$PASSWORD server > script.sh\n"
                        + "    '''\n"
                        + "  }\n"
                        + "}", true));
                story.j.assertLogNotContains("s3cr3t", story.j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
                FilePath script = story.j.jenkins.getWorkspaceFor(p).child("script.sh");
                assertTrue(script.exists());
                assertEquals("curl -u bob:s3cr3t server", script.readToString().trim());
            }
        });
    }

    @Ignore("TODO JENKINS-26137 java.io.NotSerializableException: hudson.slaves.WorkspaceList$1")
    @Test public void cleanupAfterRestart() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File originalSecret = new File(story.j.jenkins.root, "secret.txt");
                FileUtils.write(originalSecret, "s3cr3t");
                FileCredentialsImpl c = new FileCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", new FileParameterValue.FileItemImpl(originalSecret), null, null);
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                story.j.createSlave("myslave", null, null);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node('myslave') {"
                        + "  withCredentials([[$class: 'FileBinding', variable: 'SECRET', credentialsId: 'creds']]) {\n"
                        + "    semaphore 'cleanupAfterRestart'\n"
                        + "    sh 'cp $SECRET key'\n"
                        + "  }\n"
                        + "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("cleanupAfterRestart/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("cleanupAfterRestart/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b = p.getBuildByNumber(1);
                assertNotNull(b);
                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogNotContains("s3cr3t", b);
                FilePath key = story.j.jenkins.getWorkspaceFor(p).child("key");
                assertTrue(key.exists());
                assertEquals("s3cr3t", key.readToString());
            }
        });
    }

}
