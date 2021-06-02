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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.Item;
import hudson.model.FreeStyleProject;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.junit.Test;

import static org.junit.Assert.*;
import org.junit.ClassRule;

import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class UsernamePasswordMultiBindingTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @ClassRule public static BuildWatcher bw = new BuildWatcher();

    @Test public void basics() throws Exception {
        String username = "bob";
        String password = "s3cr3t";
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, "sample", username, password);
        c.setUsernameSecret(true);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<MultiBinding<?>>singletonList(new UsernamePasswordMultiBinding("userid", "pass", c.getId()))));
        if (Functions.isWindows()) {
            p.getBuildersList().add(new BatchFile("echo %userid%/%pass% > auth.txt"));
        } else {
            p.getBuildersList().add(new Shell("echo $userid/$pass > auth.txt"));
        }
        r.configRoundtrip((Item)p);
        SecretBuildWrapper wrapper = p.getBuildWrappersList().get(SecretBuildWrapper.class);
        assertNotNull(wrapper);
        List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
        assertEquals(1, bindings.size());
        MultiBinding<?> binding = bindings.get(0);
        assertEquals(c.getId(), binding.getCredentialsId());
        assertEquals(UsernamePasswordMultiBinding.class, binding.getClass());
        assertEquals("userid", ((UsernamePasswordMultiBinding) binding).getUsernameVariable());
        assertEquals("pass", ((UsernamePasswordMultiBinding) binding).getPasswordVariable());
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        r.assertLogNotContains(password, b);
        r.assertLogNotContains(username, b);
        assertEquals(username + '/' + password, b.getWorkspace().child("auth.txt").readToString().trim());
        assertEquals("[pass, userid]", new TreeSet<>(b.getSensitiveBuildVariables()).toString());
        c.setUsernameSecret(false);
        b = r.buildAndAssertSuccess(p);
        r.assertLogNotContains(password, b);
        r.assertLogContains(username, b);
        assertEquals(username + '/' + password, b.getWorkspace().child("auth.txt").readToString().trim());
        assertEquals("[pass]", new TreeSet<>(b.getSensitiveBuildVariables()).toString());
    }

}
