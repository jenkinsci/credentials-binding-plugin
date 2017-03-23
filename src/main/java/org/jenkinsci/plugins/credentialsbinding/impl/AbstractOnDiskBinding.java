package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;
import java.util.UUID;

import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.cloudbees.plugins.credentials.common.StandardCredentials;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.slaves.WorkspaceList;

/**
 * Base class for writing credentials to a file or directory, and binding its path to a single variable. Handles
 * creation of a -rwx------ temporary directory, and its full deletion when unbinding.
 * @param <C> a kind of credentials
 */
public abstract class AbstractOnDiskBinding<C extends StandardCredentials> extends Binding<C> {

    protected AbstractOnDiskBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override
    public final SingleEnvironment bindSingle(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        final FilePath secrets = secretsDir(workspace);
        final String dirName = UUID.randomUUID().toString();
        final FilePath dir = secrets.child(dirName);
        dir.mkdirs();
        secrets.chmod(0700);
        dir.chmod(0700);
        final FilePath secret = write(getCredentials(build), dir);
        return new SingleEnvironment(secret.getRemote(), new UnbinderImpl(dirName));
    }

    /**
     * Writes credentials under a given temporary directory, and returns their path (will be bound to the variable).
     * @param credentials the credentials to bind
     * @param dir a temporary directory where credentials should be written. You can assume it has already been created,
     *            with secure permissions.
     * @return the path to the on-disk credentials, to be bound to the variable
     * @throws IOException
     * @throws InterruptedException
     */
    abstract protected FilePath write(C credentials, FilePath dir) throws IOException, InterruptedException;

    @Restricted(NoExternalUse.class)
    protected static class UnbinderImpl implements Unbinder {
        private static final long serialVersionUID = 1;
        private final String dirName;

        protected UnbinderImpl(String dirName) {
            this.dirName = dirName;
        }

        @Override
        public void unbind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            secretsDir(workspace).child(dirName).deleteRecursive();
        }
    }

    private static FilePath secretsDir(FilePath workspace) {
        return tempDir(workspace).child("secretFiles");
    }

    // TODO 1.652 use WorkspaceList.tempDir
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

}
