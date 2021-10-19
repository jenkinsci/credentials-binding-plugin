package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;

import com.cloudbees.plugins.credentials.common.StandardCredentials;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Base class for writing credentials to a file or directory, and binding its path to a single variable. Handles
 * creation of a -rwx------ temporary directory, and its full deletion when unbinding, using {@link UnbindableDir}.
 * This can only safely be used for binding implementations for which {@link BindingDescriptor#requiresWorkspace()}
 * is true.
 * @param <C> a kind of credentials
 */
public abstract class AbstractOnDiskBinding<C extends StandardCredentials> extends Binding<C> {

    protected AbstractOnDiskBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override
    public final SingleEnvironment bindSingle(@NonNull Run<?, ?> build,
                                              FilePath workspace,
                                              Launcher launcher,
                                              @NonNull TaskListener listener) throws IOException, InterruptedException {
        if (workspace == null) {
            throw new IllegalArgumentException("This Binding implementation requires a non-null workspace");
        }
        final UnbindableDir dir = UnbindableDir.create(workspace);
        final FilePath secret = write(getCredentials(build), dir.getDirPath());
        return new SingleEnvironment(secret.getRemote(), dir.getUnbinder());
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

}
