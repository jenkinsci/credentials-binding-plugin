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

import java.util.Collections;

import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerDomainSpecification;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;

import hudson.FilePath;

import static org.hamcrest.Matchers.instanceOf;
import static org.jenkinsci.plugins.credentialsbinding.impl.BindingStepTest.grep;
import static org.junit.Assert.*;

public class DockerServerCredentialsBindingTest {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        story.addStep(new Statement() {
            @SuppressWarnings("rawtypes")
            @Override
            public void evaluate() throws Throwable {
                CredentialsStore store = CredentialsProvider.lookupStores(story.j.getInstance()).iterator().next();
                assertThat(store, instanceOf(SystemCredentialsProvider.StoreImpl.class));
                Domain domain = new Domain("docker", "A domain for docker credentials",
                        Collections.<DomainSpecification> singletonList(new DockerServerDomainSpecification()));
                DockerServerCredentials c = new DockerServerCredentials(CredentialsScope.GLOBAL,
                        "docker-client-cert", "desc", "clientKey", "clientCertificate", "serverCaCertificate");
                store.addDomain(domain, c);
                BindingStep s = new StepConfigTester(story.j)
                        .configRoundTrip(new BindingStep(Collections.<MultiBinding> singletonList(
                                new DockerServerCredentialsBinding("DOCKER_CERT_PATH", "docker-client-cert"))));
                story.j.assertEqualDataBoundBeans(s.getBindings(), Collections.singletonList(
                        new DockerServerCredentialsBinding("DOCKER_CERT_PATH", "docker-client-cert")));
            }
        });
    }

    @Test
    public void basics() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                DockerServerCredentials c = new DockerServerCredentials(CredentialsScope.GLOBAL,
                        "docker-client-cert", "desc", "clientKey", "clientCertificate", "serverCaCertificate");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), c);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  withCredentials([[$class: 'DockerServerCredentialsBinding',\n"
                        + "                    variable: 'DOCKER_CERT_PATH',\n"
                        + "                    credentialsId: 'docker-client-cert']]) {\n"
                        + "    semaphore 'basics'\n"
                        + "\n"
                        + "    sh '''\n"
                        + "      set -e -x\n"
                        + "      # check permissions on the credentials dir and its parent\n"
                        + "      [ $(stat -c %a \"$DOCKER_CERT_PATH\")    = 700 ]\n"
                        + "      [ $(stat -c %a \"$DOCKER_CERT_PATH\"/..) = 700 ]\n"
                        + "\n"
                        + "      # check permissions and content of the certificate files\n"
                        + "      [ $(stat -c %a \"$DOCKER_CERT_PATH/key.pem\")  = 600 ]\n"
                        + "      [ $(stat -c %a \"$DOCKER_CERT_PATH/cert.pem\") = 600 ]\n"
                        + "      [ $(stat -c %a \"$DOCKER_CERT_PATH/ca.pem\")   = 600 ]\n"
                        + "      [ $(stat -c %s \"$DOCKER_CERT_PATH/key.pem\")  = 9 ]\n"
                        + "      [ $(stat -c %s \"$DOCKER_CERT_PATH/cert.pem\") = 17 ]\n"
                        + "      [ $(stat -c %s \"$DOCKER_CERT_PATH/ca.pem\")   = 19 ]\n"
                        + "\n"
                        + "      # keep location of the certificate dir for the next step\n"
                        + "      echo \"$DOCKER_CERT_PATH\" > cert-path"
                        + "    '''\n"
                        + "  }\n"
                        + "\n"
                        + "  sh '''\n"
                        + "    set -e +x\n"
                        + "    # make sure the credentials dir have been deleted\n"
                        + "    cert_path=$(cat cert-path)\n"
                        + "    if [ -e \"$cert_path\" ] ; then\n"
                        + "      echo \"$cert_path still exists!!!\" >&2\n"
                        + "      exit 1\n"
                        + "    fi\n"
                        + "  '''\n"
                        + "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("basics/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b = p.getBuildByNumber(1);
                assertNotNull(b);
                SemaphoreStep.success("basics/1", null);
                while (b.isBuilding()) { // TODO 1.607+ use waitForCompletion
                    Thread.sleep(100);
                }
                story.j.assertBuildStatusSuccess(b);
                FilePath certPathFile = story.j.jenkins.getWorkspaceFor(p).child("cert-path");
                assertTrue(certPathFile.exists());
                String certPath = certPathFile.readToString().trim();
                // expected .../workspace/p@tmp/secretFiles/<36-chars-UUID>
                assertTrue(certPath.matches(".*/workspace/p@tmp/secretFiles/[-a-f0-9]{36}"));
                // this path is a secret, it shouldn't appear in the logs (although it doesn't really matter)
                assertEquals(Collections.<String> emptySet(), grep(b.getRootDir(), certPath));
            }
        });
    }

}