/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.jvnet.hudson.test.JenkinsRule.getLog;

@WithJenkins
class Security3672Test {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Issue("SECURITY-3672")
    @Test
    void fileBindingPathTraversal() throws Exception {
        String fileName = "p0wn3d.txt";
        String fileContent = "Hello World";
        String payload = "../../../../" + fileName;

        FileCredentialsImpl fc = new FileCredentialsImpl(
                CredentialsScope.GLOBAL,
                "randomID",
                "SECURITY-3672",
                payload,
                SecretBytes.fromBytes(fileContent.getBytes(StandardCharsets.UTF_8))
        );

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), fc);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([file(credentialsId: 'randomID', variable: 'F')]) {\n" +
                        "    listFilesInWorkspace()\n" +
                        "    echo \"Content: ${readFile(env.F)}\"\n" +
                        "  }\n" +
                        "}", true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Content: " + fileContent, run);
        // The workspace is cleaned by the UnbindableDir#unbind method, we ensure the file existed inside the workspace during the execution.
        // File name is a secret thus masked.
        r.assertLogContains("****", run);

        FilePath workspace = r.jenkins.getWorkspaceFor(p);
        FilePath secretsDir = UnbindableDir.create(workspace).getDirPath();
        FilePath resolvedPath = secretsDir.child(payload);

        assertFalse(resolvedPath.exists(), "File should not exist outside secure directory");
    }

    @Issue("SECURITY-3672")
    @Test
    void zipFileBindingPathTraversal() throws Exception {
        String fileName = "p0wn3d.zip";
        String payload = "../../../../" + fileName;

        FileCredentialsImpl fc = new FileCredentialsImpl(
                CredentialsScope.GLOBAL,
                "randomID",
                "SECURITY-3672",
                payload,
                SecretBytes.fromBytes(IOUtils.toByteArray(Security3672Test.class.getResource("a.zip")))
        );

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), fc);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([zip(credentialsId: 'randomID', variable: 'Z')]) {\n" +
                        "    listFilesInWorkspace()\n" +
                        "    echo \"Content: ${readFile(env.Z + '/dir/testfile.txt')}\"\n" +
                        "  }\n" +
                        "}", true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Content: Test of ZipFileBinding", run);
        // The workspace is cleaned by the UnbindableDir#unbind method, we ensure the zip file existed inside the workspace during the execution.
        // File name is a secret thus masked.
        r.assertLogContains("****", run);

        FilePath workspace = r.jenkins.getWorkspaceFor(p);
        FilePath secretsDir = UnbindableDir.create(workspace).getDirPath();
        FilePath resolvedPath = secretsDir.child(payload);

        assertFalse(resolvedPath.exists(), "Directory should not exist outside secure directory");
    }

    @Issue("SECURITY-3790")
    @Test
    void fileBindingPathTraversalBackslash() throws Exception {
        String fileName = "p0wn3d.txt";
        String fileContent = "Hello World";
        String payload = "..\\..\\..\\..\\" + fileName;

        FileCredentialsImpl fc = new FileCredentialsImpl(
                CredentialsScope.GLOBAL,
                "randomID",
                "SECURITY-3790",
                payload,
                SecretBytes.fromBytes(fileContent.getBytes(StandardCharsets.UTF_8))
        );

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), fc);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([file(credentialsId: 'randomID', variable: 'F')]) {\n" +
                        "    listFilesInWorkspace()\n" +
                        "    echo \"Content: ${readFile(env.F)}\"\n" +
                        "  }\n" +
                        "}", true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Content: " + fileContent, run);
        // The workspace is cleaned by the UnbindableDir#unbind method, we ensure the file existed inside the workspace during the execution.
        // File name is a secret thus masked.
        r.assertLogContains("****", run);

        FilePath workspace = r.jenkins.getWorkspaceFor(p);
        FilePath secretsDir = UnbindableDir.create(workspace).getDirPath();
        FilePath resolvedPath = secretsDir.child(payload);

        assertFalse(resolvedPath.exists(), "File should not exist outside secure directory");
    }

    @Issue("SECURITY-3790")
    @Test
    void zipFileBindingPathTraversalBackslash() throws Exception {
        String fileName = "p0wn3d.zip";
        String payload = "..\\..\\..\\..\\" + fileName;

        FileCredentialsImpl fc = new FileCredentialsImpl(
                CredentialsScope.GLOBAL,
                "randomID",
                "SECURITY-3790",
                payload,
                SecretBytes.fromBytes(IOUtils.toByteArray(Security3672Test.class.getResource("a.zip")))
        );

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), fc);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([zip(credentialsId: 'randomID', variable: 'Z')]) {\n" +
                        "    listFilesInWorkspace()\n" +
                        "    echo \"Content: ${readFile(env.Z + '/dir/testfile.txt')}\"\n" +
                        "  }\n" +
                        "}", true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Content: Test of ZipFileBinding", run);
        // The workspace is cleaned by the UnbindableDir#unbind method, we ensure the zip file existed inside the workspace during the execution.
        // File name is a secret thus masked.
        r.assertLogContains("****", run);

        FilePath workspace = r.jenkins.getWorkspaceFor(p);
        FilePath secretsDir = UnbindableDir.create(workspace).getDirPath();
        FilePath resolvedPath = secretsDir.child(payload);

        assertFalse(resolvedPath.exists(), "Directory should not exist outside secure directory");
    }

    @Issue("SECURITY-3790")
    @Test
    void fileBindingDotDotFilename() throws Exception {
        fileBindingDegenerateFilename("..");
    }

    @Issue("SECURITY-3790")
    @Test
    void fileBindingDotFilename() throws Exception {
        fileBindingDegenerateFilename(".");
    }

    @Issue("SECURITY-3790")
    @Test
    void fileBindingEmptyFilename() throws Exception {
        fileBindingDegenerateFilename("");
    }

    @Issue("SECURITY-3790")
    @Test
    void fileBindingSlashFilename() throws Exception {
        fileBindingDegenerateFilename("/");
    }

    @Issue("SECURITY-3790")
    @Test
    void fileBindingBackslashFilename() throws Exception {
        fileBindingDegenerateFilename("\\");
    }

    private void fileBindingDegenerateFilename(String payload) throws Exception {
        FileCredentialsImpl fc = new FileCredentialsImpl(
                CredentialsScope.GLOBAL,
                "randomID",
                "SECURITY-3790",
                payload,
                SecretBytes.fromBytes("Hello World".getBytes(StandardCharsets.UTF_8))
        );

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), fc);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  withCredentials([file(credentialsId: 'randomID', variable: 'F')]) {\n" +
                        "    echo \"Bound: ${env.F}\"\n" +
                        "  }\n" +
                        "}", true));

        WorkflowRun run = p.scheduleBuild2(0).get();
        r.assertBuildStatus(Result.FAILURE, run);
        assertThat(getLog(run), anyOf(
                containsString("hudson.remoting.ProxyException: java.nio.file.AccessDeniedException: "),
                containsString("Is a directory")));
    }

    public static class ListFilesInWorkspaceStep extends Step {

        @DataBoundConstructor
        public ListFilesInWorkspaceStep() {}

        @Override
        public StepExecution start(StepContext context) {
            return StepExecutions.synchronousNonBlockingVoid(context, c -> {
                FilePath workspace = c.get(FilePath.class);
                TaskListener listener = c.get(TaskListener.class);
                // Starting on the parent given UnbindableDir create a temporary workspace dir
                for (FilePath child : workspace.getParent().list("**/*")) {
                    listener.getLogger().println(child.getRemote());
                }
            });
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "listFilesInWorkspace";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(FilePath.class, TaskListener.class);
            }
        }
    }
}
