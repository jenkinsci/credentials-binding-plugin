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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Fingerprint;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.slaves.WorkspaceList;
import hudson.util.Secret;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.security.core.Authentication;

public class BindingStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule rr = new JenkinsSessionRule();

    @SuppressWarnings("rawtypes")
    @Test public void configRoundTrip() throws Throwable {
        rr.then(r -> {
            UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", "bob", "s3cr3t");
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
            BindingStep s = new StepConfigTester(r).configRoundTrip(new BindingStep(Collections.singletonList(new UsernamePasswordBinding("userpass", "creds"))));
            r.assertEqualDataBoundBeans(s.getBindings(), Collections.singletonList(new UsernamePasswordBinding("userpass", "creds")));
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new FileCredentialsImpl(CredentialsScope.GLOBAL, "secrets", "sample", "secrets.zip",
                SecretBytes.fromBytes(new byte[] {0x50,0x4B,0x05,0x06,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}))); // https://en.wikipedia.org/wiki/Zip_(file_format)#Limits
            new SnippetizerTester(r).assertRoundTrip(new BindingStep(Collections.singletonList(new ZipFileBinding("file", "secrets"))),
                "withCredentials([[$class: 'ZipFileBinding', credentialsId: 'secrets', variable: 'file']]) {\n    // some block\n}");
        });
    }
    public static class ZipStep extends Step {
        @DataBoundConstructor public ZipStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        @TestExtension("configRoundTrip") public static class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {return "zip";}
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }
        }
        static class Execution extends SynchronousStepExecution<Void> {
            Execution(StepContext context) {
                super(context);
            }
            @Override protected Void run() {return null;}
        }
    }

    @Test public void basics() throws Throwable {
        final String credentialsId = "creds";
        final String username = "bob";
        final String password = "s$$cr3t";
        rr.then(r -> {
            UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", username, password);
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  withCredentials([usernamePassword(usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD', credentialsId: '" + credentialsId + "')]) {\n"
                + "    semaphore 'basics'\n"
                + "    if (isUnix()) {\n"
                + "      sh 'echo curl -u \"$USERNAME:$PASSWORD\" server > script'\n"
                + "    } else {\n"
                + "      bat 'echo curl -u %USERNAME%:%PASSWORD% server > script'\n"
                + "    }\n"
                + "  }\n"
                + "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("basics/1", b);
            r.assertLogContains(Functions.isWindows() ? "Masking supported pattern matches of %PASSWORD%" : "Masking supported pattern matches of $PASSWORD", b);
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            assertNotNull(p);
            WorkflowRun b = p.getBuildByNumber(1);
            assertNotNull(b);
            assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), password));
            SemaphoreStep.success("basics/1", null);
            r.waitForCompletion(b);
            r.assertBuildStatusSuccess(b);
            r.assertLogNotContains(password, b);
            FilePath script = r.jenkins.getWorkspaceFor(p).child("script");
            assertTrue(script.exists());
            assertEquals("curl -u " + username + ":" + password + " server", script.readToString().trim());
            assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), password));
        });
    }

    @Issue("JENKINS-42999")
    @Test
    public void limitedRequiredContext() throws Throwable {
        final String credentialsId = "creds";
        final String username = "bob";
        final String password = "s3cr3t";
        rr.then(r -> {
            UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", username, password);
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "withCredentials([usernameColonPassword(variable: 'USERPASS', credentialsId: '" + credentialsId + "')]) {\n"
                + "  semaphore 'basics'\n"
                + "  echo USERPASS.toUpperCase()\n"
                + "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("basics/1", b);
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            assertNotNull(p);
            WorkflowRun b = p.getBuildByNumber(1);
            assertNotNull(b);
            assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), password));
            SemaphoreStep.success("basics/1", null);
            r.waitForCompletion(b);
            r.assertBuildStatusSuccess(b);
            r.assertLogContains((username + ":" + password).toUpperCase(), b);
            r.assertLogNotContains(password, b);
        });
    }

    @Issue("JENKINS-42999")
    @Test
    public void widerRequiredContext() throws Throwable {
        final String credentialsId = "creds";
        final String credsFile = "credsFile";
        final String credsContent = "s3cr3t";
        rr.then(r -> {
            FileCredentialsImpl c = new FileCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", credsFile, SecretBytes.fromBytes(credsContent.getBytes()));
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "withCredentials([file(variable: 'targetFile', credentialsId: '" + credentialsId + "')]) {\n"
                + "  echo 'We should fail before getting here'\n"
                + "}", true));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
            r.assertLogNotContains("We should fail before getting here", b);
            r.assertLogContains("Required context class hudson.FilePath is missing", b);
            r.assertLogContains("Perhaps you forgot to surround the code with a step that provides this, such as: node", b);
        });
    }

    @Test public void incorrectType() throws Throwable {
        rr.then(r -> {
            StringCredentialsImpl c = new StringCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", Secret.fromString("s3cr3t"));
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  withCredentials([usernamePassword(usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD', credentialsId: 'creds')]) {\n"
                + "  }\n"
                + "}", true));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

            // make sure error message contains information about the actual type and the expected type
            r.assertLogNotContains("s3cr3t", b);
            r.assertLogContains(StandardUsernamePasswordCredentials.class.getName(), b); // no descriptor for the interface type
            r.assertLogContains(ExtensionList.lookupSingleton(StringCredentialsImpl.DescriptorImpl.class).getDisplayName(), b);
            r.assertLogNotContains("\tat ", b);
        });
    }

    @Test public void cleanupAfterRestart() throws Throwable {
        final String secret = "s3cr3t";
        rr.then(r -> {
            FileCredentialsImpl c = new FileCredentialsImpl(CredentialsScope.GLOBAL, "creds", "sample", "secret.txt", SecretBytes.fromBytes(secret.getBytes()));
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
            r.createSlave("myslave", null, null);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                    + "node('myslave') {"
                    + "  withCredentials([file(variable: 'SECRET', credentialsId: 'creds')]) {\n"
                    + "    semaphore 'cleanupAfterRestart'\n"
                    + "    if (isUnix()) {sh 'cp \"$SECRET\" key'} else {bat 'copy \"%SECRET%\" key'}\n"
                    + "  }\n"
                    + "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("cleanupAfterRestart/1", b);
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            assertNotNull(p);
            WorkflowRun b = p.getBuildByNumber(1);
            assertNotNull(b);
            assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), secret));
            SemaphoreStep.success("cleanupAfterRestart/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            r.assertLogNotContains(secret, b);
            FilePath ws = r.jenkins.getNode("myslave").getWorkspaceFor(p);
            FilePath key = ws.child("key");
            assertTrue(key.exists());
            assertEquals(secret, key.readToString());
            FilePath secretFiles = WorkspaceList.tempDir(ws).child("secretFiles");
            assertTrue(secretFiles.isDirectory());
            assertEquals(Collections.emptyList(), secretFiles.list());
            assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), secret));
        });
    }

    @Issue("JENKINS-27389")
    @Test public void grabEnv() throws Throwable {
        rr.then(r -> {
            String credentialsId = "creds";
            String secret = "s3cr3t";
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", Secret.fromString(secret)));
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "def extract(id) {\n"
                + "  def v\n"
                + "  withCredentials([string(credentialsId: id, variable: 'temp')]) {\n"
                + "    v = env.temp\n"
                + "  }\n"
                + "  v\n"
                + "}\n"
                + "node {\n"
                + "  echo \"got: ${extract('" + credentialsId + "')}\"\n"
                + "}", true));
            r.assertLogContains("got: " + secret, r.assertBuildStatusSuccess(p.scheduleBuild2(0).get()));
        });
    }

    @Issue("JENKINS-27486")
    @Test public void masking() throws Throwable {
        rr.then(r -> {
            String credentialsId = "creds";
            String secret = "s3cr3t";
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", Secret.fromString(secret)));
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'SECRET')]) {\n"
                // forgot set +x, ran /usr/bin/env, etc.
                + "    if (isUnix()) {sh 'echo $SECRET > oops'} else {bat 'echo %SECRET% > oops'}\n"
                + "  }\n"
                + "}", true));
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
            r.assertLogNotContains(secret, b);
            r.assertLogContains("echo ****", b);
        });
    }

    @Issue("JENKINS-30326")
    @Test
    public void testGlobalBindingWithAuthorization() throws Throwable {
        rr.then(r -> {
            // configure security
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());

            QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new DummyAuthenticator());

            String credentialsId = "creds";
            String secret = "s3cr3t";
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", Secret.fromString(secret)));
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

            p.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  withCredentials([string(credentialsId: '" + credentialsId + "', variable: 'SECRET')]) {\n"
                + "    writeFile file:'test', text: \"$env.SECRET\"\n"
                + "    def content = readFile 'test'\n"
                + "    if (\"$content\" != \"" + secret + "\") {\n"
                + "      error 'The credential was not bound into the workflow correctly'\n"
                + "    }\n"
                + "  }\n"
                + "}", true));

            // the build will fail if we can not locate the credentials
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
            // make sure this was actually run as a user and not system
            r.assertLogContains("Running as dummy", b);
        });
    }
    private static final class DummyAuthenticator extends QueueItemAuthenticator {
        @Override public Authentication authenticate2(Queue.Task task) {
            if (task instanceof WorkflowJob) {
                return User.getById("dummy", true).impersonate2();
            } else {
                return null;
            }
        }
    }

    @Issue("JENKINS-38831")
    @Test
    public void testTrackingOfCredential() throws Throwable {
        rr.then(r -> {
            String credentialsId = "creds";
            String secret = "s3cr3t";
            StringCredentialsImpl credentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", Secret.fromString(secret));
            Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);

            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), credentials);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(""
                + "def extract(id) {\n"
                + "  def v\n"
                + "  withCredentials([[$class: 'StringBinding', credentialsId: id, variable: 'temp']]) {\n"
                + "    v = env.temp\n"
                + "  }\n"
                + "  v\n"
                + "}\n"
                + "node {\n"
                + "  echo \"got: ${extract('" + credentialsId + "')}\"\n"
                + "}", true));

            assertThat("No fingerprint created until first use", fingerprint, nullValue());

            r.assertLogContains("got: " + secret, r.assertBuildStatusSuccess(p.scheduleBuild2(0).get()));

            fingerprint = CredentialsProvider.getFingerprintOf(credentials);

            assertThat(fingerprint, notNullValue());
            assertThat(fingerprint.getJobs(), hasItem(is(p.getFullName())));
        });
    }

    @Issue("JENKINS-41760")
    @Test public void emptyOrBlankCreds() throws Throwable {
        rr.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {withCredentials([]) {echo 'normal output'}}", true));
            r.assertLogContains("normal output", r.buildAndAssertSuccess(p));
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL, "creds", null, Secret.fromString("")));
            p.setDefinition(new CpsFlowDefinition("node {withCredentials([string(variable: 'SECRET', credentialsId: 'creds')]) {echo 'normal output'}}", true));
            r.assertLogContains("normal output", r.buildAndAssertSuccess(p));
        });
    }

    @Issue("JENKINS-64631")
    @Test
    public void usernameUnmaskedInStepArguments() throws Throwable {
        rr.then(r -> {
            String credentialsId = "my-credentials";
            String username = "alice";
            // UsernamePasswordCredentialsImpl.isUsernameSecret defaults to false for new credentials.
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                    new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, null, username, "s3cr3t"));
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withCredentials([usernamePassword(credentialsId: '" + credentialsId + "', usernameVariable: 'username', passwordVariable: 'password')]) {\n" +
                    "    echo(/Username is ${username}/)\n" +
                    "  }\n" +
                    "}", true));
            WorkflowRun b = r.buildAndAssertSuccess(p);
            FlowExecution exec = b.asFlowExecutionOwner().get();
            FlowNode echoNode = new DepthFirstScanner().findFirstMatch(exec, new NodeStepTypePredicate("echo"));
            assertThat(ArgumentsAction.getArguments(echoNode).get("message"), equalTo("Username is " + username));
        });
    }

    @Issue("https://github.com/jenkinsci/credentials-plugin/pull/293")
    @Test public void forRun() throws Throwable {
        rr.then(r -> {
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), new SpecialCredentials(CredentialsScope.GLOBAL, "test", null));
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("withCredentials([string(variable: 'SECRET', credentialsId: 'test')]) {echo(/got: ${SECRET.toUpperCase()}/)}", true));
            r.assertLogContains("got: P#1", r.assertBuildStatusSuccess(p.scheduleBuild2(0).get()));
        });
    }
    private static final class SpecialCredentials extends BaseStandardCredentials implements StringCredentials {
        private Run<?, ?> build;
        SpecialCredentials(CredentialsScope scope, String id, String description) {
            super(scope, id, description);
        }
        @Override public Secret getSecret() {
            return Secret.fromString(build != null ? build.getExternalizableId() : "unknown");
        }
        @Override public Credentials forRun(Run<?, ?> context) {
            SpecialCredentials clone = new SpecialCredentials(getScope(), getId(), getDescription());
            clone.build = context;
            return clone;
        }
    }

    private static Set<String> grep(File dir, String text) throws IOException {
        Set<String> matches = new TreeSet<>();
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
            } else {
                try {
                    if (FileUtils.readFileToString(kid, StandardCharsets.UTF_8).contains(text)) {
                        matches.add(qualifiedName);
                    }
                } catch (FileNotFoundException | NoSuchFileException x) {
                    // ignore, e.g. tmp file
                }
            }
        }
    }

}
