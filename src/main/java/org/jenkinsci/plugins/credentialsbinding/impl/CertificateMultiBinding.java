package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

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

	private final String passwordVariable;
	private final String aliasVariable;

	@DataBoundConstructor
	public CertificateMultiBinding(String keystoreVariable, String passwordVariable, String aliasVariable,
			String credentialsId) {
		super(credentialsId);
		this.keystoreVariable = keystoreVariable;
		this.passwordVariable = passwordVariable;
		this.aliasVariable = aliasVariable;
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
		Map<String, String> m = new HashMap<String, String>();
		m.put(aliasVariable, credentials.getDescription());
		m.put(passwordVariable, storePassword);

		if (workspace != null) {
			FilePath secrets = FileBinding.secretsDir(workspace);
			final String tempKeyStoreName = UUID.randomUUID().toString();
			final FilePath secret = secrets.child(tempKeyStoreName);
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
			return new MultiEnvironment(m, new UnbinderImpl(secret));
		} else {
			return new MultiEnvironment(m);
		}
	}

	private static class UnbinderImpl implements Unbinder {

		private static final long serialVersionUID = 1;

		private final FilePath keyStoreFile;

		UnbinderImpl(FilePath keystoreFile) {
			this.keyStoreFile = keystoreFile;
		}

		@Override
		public void unbind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
				throws IOException, InterruptedException {
			keyStoreFile.delete();
		}

	}

	@Override
	public Set<String> variables() {
		return new HashSet<String>(Arrays.asList(keystoreVariable, passwordVariable, aliasVariable));
	}

	@Extension
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
