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
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.FilePath;
import hudson.model.FileParameterValue;
import hudson.model.Node;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.util.Secret;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class BindingStepTest {

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

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
        final String credentialsId = "creds";
        final String username = "bob";
        final String password = "s3cr3t";
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", username, password);
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  withCredentials([[$class: 'UsernamePasswordMultiBinding', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD', credentialsId: '" + credentialsId + "']]) {\n"
                        + "    semaphore 'basics'\n"
                        + "    sh '''\n"
                        + "      set +x\n"
                        + "      echo curl -u $USERNAME:$PASSWORD server > script.sh\n"
                        + "    '''\n"
                        + "  }\n"
                        + "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("basics/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b = p.getBuildByNumber(1);
                assertNotNull(b);
                assertEquals("TODO JENKINS-27631", Collections.singleton("program.dat"), grep(b.getRootDir(), password));
                SemaphoreStep.success("basics/1", null);
                while (b.isBuilding()) { // TODO 1.607+ use waitForCompletion
                    Thread.sleep(100);
                }
                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogNotContains(password, b);
                FilePath script = story.j.jenkins.getWorkspaceFor(p).child("script.sh");
                assertTrue(script.exists());
                assertEquals("curl -u " + username + ":" + password + " server", script.readToString().trim());
                assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), password));
            }
        });
    }

    @Inject
    StringCredentialsImpl.DescriptorImpl stringCredentialsDescriptor;

    @Test public void incorrectType() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                StringCredentialsImpl c = new StringCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", Secret.fromString("s3cr3t"));
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  withCredentials([[$class: 'UsernamePasswordMultiBinding', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD', credentialsId: 'creds']]) {\n"
                        + "  }\n"
                        + "}", true));
                WorkflowRun r = story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

                // make sure error message contains information about the actual type and the expected type
                story.j.assertLogNotContains("s3cr3t", r);
                story.j.assertLogContains(CredentialNotFoundException.class.getName(), r);
                story.j.assertLogContains(StandardUsernamePasswordCredentials.class.getName(), r);
                story.j.assertLogContains(stringCredentialsDescriptor.getDisplayName(), r);
                System.out.println(JenkinsRule.getLog(r)); // TODO 1.607+ use BuildWatcher
            }
        });
    }

    @Test public void cleanupAfterRestart() throws Exception {
        final String secret = "s3cr3t";
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File originalSecret = tmp.newFile();
                FileUtils.write(originalSecret, secret);
                FileCredentialsImpl c = new FileCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", new FileParameterValue.FileItemImpl(originalSecret), null, null);
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                // TODO JENKINS-26398: story.j.createSlave("myslave", null, null) does not work since the slave root is deleted after restart.
                story.j.jenkins.addNode(new DumbSlave("myslave", "", tmp.newFolder().getAbsolutePath(), "1", Node.Mode.NORMAL, "", story.j.createComputerLauncher(null), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList()));
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
                while (b.isBuilding()) { // TODO 1.607+ use waitForCompletion
                    Thread.sleep(100);
                }
                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogNotContains(secret, b);
                FilePath key = story.j.jenkins.getNode("myslave").getWorkspaceFor(p).child("key");
                assertTrue(key.exists());
                assertEquals(secret, key.readToString());
                FilePath secretFiles = story.j.jenkins.getNode("myslave").getRootPath().child("secretFiles");
                assertTrue(secretFiles.isDirectory());
                assertEquals(Collections.emptyList(), secretFiles.list());
                assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), secret));
            }
        });
    }

    private static Set<String> grep(File dir, String text) throws IOException {
        Set<String> matches = new TreeSet<String>();
        grep(dir, text, "", matches);
        return matches;
    }
    private static void grep(File dir, String text, String prefix, Set<String> matches) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            String qualifiedName = prefix + kid.getName();
            if (kid.isDirectory()) {
                grep(kid, text, qualifiedName + "/", matches);
            } else if (kid.isFile() && FileUtils.readFileToString(kid).contains(text)) {
                matches.add(qualifiedName);
            }
        }
    }

}
