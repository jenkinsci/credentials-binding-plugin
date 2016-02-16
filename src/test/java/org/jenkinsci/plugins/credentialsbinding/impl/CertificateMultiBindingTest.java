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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

public class CertificateMultiBindingTest {

	@Rule
	public JenkinsRule r = new JenkinsRule();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	File certificate;

	@Before
	public void setUp() throws IOException {
		/* do the dance to get a simple zip file into jenkins */
		InputStream stream = this.getClass().getResourceAsStream("certificate.p12");
		try {
			assertThat(stream, is(not(nullValue())));
			certificate = tmp.newFile("a.certificate");
			FileUtils.copyInputStreamToFile(stream, certificate);
		} finally {
			IOUtils.closeQuietly(stream);
			stream = null;
		}
	}

	@Test
	public void basics() throws Exception {
		String alias = "androiddebugkey";
		String password = "android";
		StandardCertificateCredentials c = new CertificateCredentialsImpl(CredentialsScope.GLOBAL, null, alias,
				password, new CertificateCredentialsImpl.FileOnMasterKeyStoreSource(certificate.getAbsolutePath()));
		CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
		FreeStyleProject p = r.createFreeStyleProject();
		p.getBuildWrappersList().add(new SecretBuildWrapper(Collections
				.<MultiBinding<?>> singletonList(new CertificateMultiBinding("keystore", "password", "alias", c.getId()))));
		if (Functions.isWindows()) {
			p.getBuildersList().add(new BatchFile(
					  "@echo off\n"
					+ "echo | set /p=\"%alias%/%password%/\" > secrets.txt\n"
					+ "IF EXIST %keystore% (\n"
					+ "echo | set /p=\"exists\" >> secrets.txt\n"
					+ ") ELSE (\n"
					+ "echo | set /p=\"missing\" >> secrets.txt\n"
					+ ")"));
		} else {
			p.getBuildersList().add(new Shell(
					  "set +x\n"
					+ "printf $alias/$password/ > secrets.txt\n"
					+ "if [ -f \"$keystore\" ]\n"
					+ "then\n"
					+ "printf exists >> secrets.txt\n"
					+ "else\n"
					+ "printf missing >> secrets.txt\n"
					+ "fi"));
		}
		r.configRoundtrip((Item) p);
		SecretBuildWrapper wrapper = p.getBuildWrappersList().get(SecretBuildWrapper.class);
		assertNotNull(wrapper);
		List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
		assertEquals(1, bindings.size());
		MultiBinding<?> binding = bindings.get(0);
		assertEquals(c.getId(), binding.getCredentialsId());
		assertEquals(CertificateMultiBinding.class, binding.getClass());
		assertEquals("password", ((CertificateMultiBinding) binding).getPasswordVariable());
		assertEquals("alias", ((CertificateMultiBinding) binding).getAliasVariable());
		assertEquals("keystore", ((CertificateMultiBinding) binding).getKeystoreVariable());
		FreeStyleBuild b = r.buildAndAssertSuccess(p);
		r.assertLogNotContains(password, b);
		assertEquals(alias + '/' + password + "/exists", b.getWorkspace().child("secrets.txt").readToString().trim());
		assertEquals(b.getSensitiveBuildVariables(), containsInAnyOrder("keystore", "password", "alias"));
	}

}
