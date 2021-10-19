package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;
import java.util.UUID;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding.Unbinder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;

/**
 * Convenience class for creating a secure temporary directory dedicated to writing credentials file(s), and getting a
 * corresponding {@link Unbinder} instance.
 */
public class UnbindableDir {

    private final FilePath dirPath;
    private final Unbinder unbinder;

    private UnbindableDir(FilePath dirPath) {
        this.dirPath = dirPath;
        this.unbinder = new UnbinderImpl(dirPath.getName());
    }

    public Unbinder getUnbinder() {
        return unbinder;
    }

    public FilePath getDirPath() {
        return dirPath;
    }

    /**
     * Creates a new, secure, directory under a base workspace temporary directory. Also instantiates
     * an {@link Unbinder} for deleting this directory later. This can only safely be used for binding
     * implementations for which {@link BindingDescriptor#requiresWorkspace()} is true.
     * @param workspace The workspace, can't be null (temporary dirs are created next to it)
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static UnbindableDir create(@NonNull FilePath workspace)
            throws IOException, InterruptedException {
        final FilePath secrets = secretsDir(workspace);
        final String dirName = UUID.randomUUID().toString();
        final FilePath dir = secrets.child(dirName);
        dir.mkdirs();
        secrets.chmod(0700);
        dir.chmod(0700);
        return new UnbindableDir(dir);
    }

    private static FilePath secretsDir(FilePath workspace) {
        return tempDir(workspace).child("secretFiles");
    }

    // TODO 1.652 use WorkspaceList.tempDir
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    @Restricted(NoExternalUse.class)
    protected static class UnbinderImpl implements Unbinder {
        private static final long serialVersionUID = 1;
        private final String dirName;

        protected UnbinderImpl(String dirName) {
            this.dirName = dirName;
        }

        @Override
        public void unbind(@NonNull Run<?, ?> build,
                FilePath workspace,
                Launcher launcher,
                @NonNull TaskListener listener) throws IOException, InterruptedException {
            secretsDir(workspace).child(dirName).deleteRecursive();
        }
    }

}
