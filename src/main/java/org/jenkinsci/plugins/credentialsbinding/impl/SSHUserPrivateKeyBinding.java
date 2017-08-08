/*
 * The MIT License
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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

public class SSHUserPrivateKeyBinding extends MultiBinding<SSHUserPrivateKey> {

    public final String keyFileVariable;
    public String usernameVariable;
    public String passphraseVariable;

    @DataBoundConstructor public SSHUserPrivateKeyBinding(@Nonnull String keyFileVariable, String credentialsId) {
        super(credentialsId);
        this.keyFileVariable = keyFileVariable;
    }

    @DataBoundSetter
    public void setUsernameVariable(@Nonnull final String usernameVariable) {
        this.usernameVariable = usernameVariable;
    }

    @CheckForNull
    public String getUsernameVariable() {
        return usernameVariable;
    }

    @DataBoundSetter
    public void setPassphraseVariable(@Nonnull final String passphraseVariable) {
        this.passphraseVariable = passphraseVariable;
    }

    @CheckForNull
    public String getPassphraseVariable() {
        return passphraseVariable;
    }

    @Override protected Class<SSHUserPrivateKey> type() {
        return SSHUserPrivateKey.class;
    }

    @Override public Set<String> variables() {
        Set<String> set = new HashSet<>();
        set.add(keyFileVariable);
        if (usernameVariable != null) {
            set.add(usernameVariable);
        }
        if (passphraseVariable != null) {
            set.add(passphraseVariable);
        }
        return ImmutableSet.copyOf(set);
    }

    @Override public MultiEnvironment bind(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        SSHUserPrivateKey sshKey = getCredentials(build);
        UnbindableDir keyDir = UnbindableDir.create(workspace);
        FilePath keyFile =  keyDir.getDirPath().child("ssh-key-" + keyFileVariable);

        StringBuilder contents = new StringBuilder();
        for (String key : sshKey.getPrivateKeys()) {
            contents.append(key);
            contents.append('\n');
        }
        keyFile.write(contents.toString(), "UTF-8");
        keyFile.chmod(0400);

        Map<String, String> map = new HashMap<String, String>();
        map.put(keyFileVariable, keyFile.getRemote());
        if (passphraseVariable != null) {
            Secret passphrase = sshKey.getPassphrase();
            if (passphrase != null) {
                map.put(passphraseVariable, passphrase.getPlainText());
            } else {
                map.put(passphraseVariable, "");
            }
        }
        if (usernameVariable != null) {
            map.put(usernameVariable, sshKey.getUsername());
        }

        return new MultiEnvironment(map, keyDir.getUnbinder());
    }

    @Symbol("sshUserPrivateKey")
    @Extension public static class DescriptorImpl extends BindingDescriptor<SSHUserPrivateKey> {

        @Override protected Class<SSHUserPrivateKey> type() {
            return SSHUserPrivateKey.class;
        }

        @Override public String getDisplayName() {
            return Messages.SSHUserPrivateKeyBinding_ssh_user_private_key();
        }

    }

}
