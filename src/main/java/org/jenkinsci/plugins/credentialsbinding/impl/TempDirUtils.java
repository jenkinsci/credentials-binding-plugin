package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;

import org.jenkinsci.plugins.credentialsbinding.MultiBinding.Unbinder;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.slaves.WorkspaceList;

/*package*/abstract class TempDirUtils {

    /* package */static FilePath secretsDir(FilePath workspace) {
        return tempDir(workspace).child("secretFiles");
    }

    // TODO 1.652 use WorkspaceList.tempDir
    /* package */static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    /* package */static class UnbinderImpl implements Unbinder {

        private static final long serialVersionUID = 1;

        private final String dirName;

        UnbinderImpl(String dirName) {
            this.dirName = dirName;
        }

        @Override
        public void unbind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException {
            secretsDir(workspace).child(dirName).deleteRecursive();
        }

    }

}
