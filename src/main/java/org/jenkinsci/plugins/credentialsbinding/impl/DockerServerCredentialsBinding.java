package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;
import java.util.UUID;

import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;

import static org.jenkinsci.plugins.credentialsbinding.impl.TempDirUtils.*;

public class DockerServerCredentialsBinding extends Binding<DockerServerCredentials> {

    @DataBoundConstructor
    public DockerServerCredentialsBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override
    protected Class<DockerServerCredentials> type() {
        return DockerServerCredentials.class;
    }

    @Override
    public SingleEnvironment bindSingle(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        DockerServerCredentials credentials = getCredentials(build);
        FilePath secrets = secretsDir(workspace);
        String dirName = UUID.randomUUID().toString();
        final FilePath dir = secrets.child(dirName);
        dir.mkdirs();
        secrets.chmod(0700);
        dir.chmod(0700);

        FilePath clientKey = dir.child("key.pem");
        clientKey.write(credentials.getClientKey(), null);
        clientKey.chmod(0600);

        FilePath clientCert = dir.child("cert.pem");
        clientCert.write(credentials.getClientCertificate(), null);
        clientCert.chmod(0600);

        FilePath serverCACert = dir.child("ca.pem");
        serverCACert.write(credentials.getServerCaCertificate(), null);
        serverCACert.chmod(0600);

        return new SingleEnvironment(dir.getRemote(), new UnbinderImpl(dirName));
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BindingDescriptor<DockerServerCredentials> {

        @Override
        protected Class<DockerServerCredentials> type() {
            return DockerServerCredentials.class;
        }

        @Override
        public String getDisplayName() {
            return Messages.DockerServerCredentialsBinding_docker_client_certificate();
        }

    }

}
