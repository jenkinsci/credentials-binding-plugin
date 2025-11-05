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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.FileParameterValue;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CertificateMultiBindingTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

	private JenkinsRule r;

	@TempDir
	private File tmp;

	private File certificate;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        r = rule;
		/* do the dance to get a simple zip file into jenkins */
		certificate = newFile(tmp, "a.certificate");
		final URL resource = this.getClass().getResource("certificate.p12");
		assertThat(resource, is(not(nullValue())));
		FileUtils.copyURLToFile(resource, certificate);
	}

    // TODO configRoundtrip to test form, null hygiene on @DataBoundSetter

    @Test
    void basics() throws Exception {
		String alias = "androiddebugkey";
		String password = "android";
		StandardCertificateCredentials c = new CertificateCredentialsImpl(CredentialsScope.GLOBAL, null, alias,
				password, new CertificateCredentialsImpl.UploadedKeyStoreSource(new FileParameterValue.FileItemImpl(certificate), null));
		CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
		FreeStyleProject p = r.createFreeStyleProject();
		CertificateMultiBinding binding = new CertificateMultiBinding("keystore", c.getId());
		binding.setAliasVariable("alias");
		binding.setPasswordVariable("password");
		p.getBuildWrappersList().add(new SecretBuildWrapper(Collections
				.<MultiBinding<?>> singletonList(binding)));
		if (Functions.isWindows()) {
			p.getBuildersList().add(new BatchFile(
                    """
                            echo | set /p="%alias%/%password%/" > secrets.txt\r
                            IF EXIST "%keystore%" (\r
                            echo | set /p="exists" >> secrets.txt\r
                            ) ELSE (\r
                            echo | set /p="missing" >> secrets.txt\r
                            )\r
                            exit 0"""));
		} else {
			p.getBuildersList().add(new Shell(
                    """
                            printf $alias/$password/ > secrets.txt
                            if [ -f "$keystore" ]
                            then
                            printf exists >> secrets.txt
                            else
                            printf missing >> secrets.txt
                            fi"""));
		}
		r.configRoundtrip((Item) p);
		SecretBuildWrapper wrapper = p.getBuildWrappersList().get(SecretBuildWrapper.class);
		assertNotNull(wrapper);
		List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
		assertEquals(1, bindings.size());
		MultiBinding<?> testBinding = bindings.get(0);
		assertEquals(c.getId(), testBinding.getCredentialsId());
		assertEquals(CertificateMultiBinding.class, testBinding.getClass());
		assertEquals("password", ((CertificateMultiBinding) testBinding).getPasswordVariable());
		assertEquals("alias", ((CertificateMultiBinding) testBinding).getAliasVariable());
		assertEquals("keystore", ((CertificateMultiBinding) testBinding).getKeystoreVariable());
		FreeStyleBuild b = r.buildAndAssertSuccess(p);
		r.assertLogNotContains(password, b);
		assertEquals(alias + '/' + password + "/exists", b.getWorkspace().child("secrets.txt").readToString().trim());
		assertThat(b.getSensitiveBuildVariables(), containsInAnyOrder("keystore", "password", "alias"));
	}

    @Test
    void basicsPipeline() throws Exception {
		// create the Credentials
		String alias = "androiddebugkey";
		String password = "android";
		StandardCertificateCredentials c = new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "my-certificate", alias,
				password, new CertificateCredentialsImpl.UploadedKeyStoreSource(new FileParameterValue.FileItemImpl(certificate), null));
		CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
		// create the Pipeline job
		WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
		String pipelineScript = getTestResourceAsUTF8String("basicsPipeline-Jenkinsfile");
		p.setDefinition(new CpsFlowDefinition(pipelineScript, true));
		// copy resources into workspace
		FilePath workspace = r.jenkins.getWorkspaceFor(p);
		copyTestResourceIntoWorkspace(workspace, "basicsPipeline-step1.bat", 0755);
		copyTestResourceIntoWorkspace(workspace, "basicsPipeline-step2.bat", 0755);
		copyTestResourceIntoWorkspace(workspace, "basicsPipeline-step1.sh", 0755);
		copyTestResourceIntoWorkspace(workspace, "basicsPipeline-step2.sh", 0755);
		// execute the pipeline
		WorkflowRun b = p.scheduleBuild2(0).waitForStart();
		r.waitForCompletion(b);
		r.assertBuildStatusSuccess(b);
	}

	private InputStream getTestResourceInputStream(String fileName) {
		return getClass().getResourceAsStream(getClass().getSimpleName() + "/" + fileName);
	}

	private String getTestResourceAsUTF8String(String resourceName) throws IOException {
		try (InputStream is = getTestResourceInputStream(resourceName)) {
			return IOUtils.toString(is, StandardCharsets.UTF_8);
		}
	}

	private FilePath copyTestResourceIntoWorkspace(FilePath workspace, String fileName, int mask)
			throws IOException, InterruptedException {
		try (InputStream in = getTestResourceInputStream(fileName)) {
			FilePath f = workspace.child(fileName);
			f.copyFrom(in);
			f.chmod(mask);
			return f;
		}
	}

    private static File newFile(File parent, String child) throws IOException {
        File result = new File(parent, child);
        result.createNewFile();
        return result;
    }

}
