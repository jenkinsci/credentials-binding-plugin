package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.LinkedHashMap;

public class CertificateMultiBinding extends MultiBinding<StandardCertificateCredentials> {

	private final String keystoreVariable;

	public String getKeystoreVariable() {
		return keystoreVariable;
	}

	public String getPasswordVariable() {
		return passwordVariable;
	}

	public String getAliasVariable() {
		return aliasVariable;
	}

	private String passwordVariable;
	
	@DataBoundSetter
	public void setPasswordVariable(String passwordVariable) {
		this.passwordVariable = passwordVariable;
	}

	@DataBoundSetter
	public void setAliasVariable(String aliasVariable) {
		this.aliasVariable = aliasVariable;
	}

	private String aliasVariable;
	// TODO JENKINS-44860 consider adding a showAlias field

	@DataBoundConstructor
	@CheckForNull
	public CertificateMultiBinding(@Nonnull String keystoreVariable, String credentialsId) {
		super(credentialsId);
		this.keystoreVariable = keystoreVariable;
	}

	@Override
	protected Class<StandardCertificateCredentials> type() {
		return StandardCertificateCredentials.class;
	}

	@Override
	public org.jenkinsci.plugins.credentialsbinding.MultiBinding.MultiEnvironment bind(Run<?, ?> build,
			FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
		StandardCertificateCredentials credentials = getCredentials(build);
		final String storePassword = credentials.getPassword().getPlainText();
		Map<String, String> m = new LinkedHashMap<>();
		if(aliasVariable!=null && !aliasVariable.isEmpty())
			m.put(aliasVariable, credentials.getDescription());
		if(passwordVariable!=null && !passwordVariable.isEmpty())
			m.put(passwordVariable, storePassword);

		if (workspace != null) {
			final UnbindableDir secrets = UnbindableDir.create(workspace);
			final FilePath secret = secrets.getDirPath().child("keystore-" + keystoreVariable);
			OutputStream out = secret.write();
			try {
				credentials.getKeyStore().store(out, storePassword.toCharArray());
			} catch (KeyStoreException e) {
				throw new IOException(e);
			} catch (NoSuchAlgorithmException e) {
				throw new IOException(e);
			} catch (CertificateException e) {
				throw new IOException(e);
			} finally {
				org.apache.commons.io.IOUtils.closeQuietly(out);
			}
			secret.chmod(0400);
			m.put(keystoreVariable, secret.getRemote());
			return new MultiEnvironment(m, secrets.getUnbinder());
		} else {
			return new MultiEnvironment(m);
		}
	}

	@Override
	public Set<String> variables() {
		Set<String> set = new HashSet<>();
		set.add(keystoreVariable);
		if (aliasVariable != null && !aliasVariable.isEmpty()) {
			set.add(aliasVariable);
		}
		if (passwordVariable != null && !passwordVariable.isEmpty()) {
			set.add(passwordVariable);
		}
		return ImmutableSet.copyOf(set);
	}

	@Extension
	@Symbol("certificate")
	public static class DescriptorImpl extends BindingDescriptor<StandardCertificateCredentials> {

		@Override
		protected Class<StandardCertificateCredentials> type() {
			return StandardCertificateCredentials.class;
		}

		@Override
		public String getDisplayName() {
			return Messages.CertificateMultiBinding_certificate_keystore();
		}

	}

}
