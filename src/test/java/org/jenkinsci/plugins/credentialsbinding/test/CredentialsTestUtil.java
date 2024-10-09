/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package org.jenkinsci.plugins.credentialsbinding.test;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.ModelObject;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import java.io.IOException;
import java.util.UUID;

public class CredentialsTestUtil {

    /**
     * Registers the given value as a {@link StringCredentials} into the default {@link CredentialsProvider}.
     * Returns the generated credential id for the registered credentials.
     */
    public static String registerStringCredentials(ModelObject context, String value) throws IOException {
        String credentialsId = UUID.randomUUID().toString();
        setStringCredentials(context, credentialsId, value);
        return credentialsId;
    }

    /**
     * Registers the given value as a {@link StringCredentials} into the default {@link CredentialsProvider} using the
     * specified credentials id.
     */
    public static void setStringCredentials(ModelObject context, String credentialsId, String value) throws IOException {
        StringCredentials creds = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, null, Secret.fromString(value));
        CredentialsProvider.lookupStores(context).iterator().next().addCredentials(Domain.global(), creds);
    }

    /**
     * Registers the given value as a {@link UsernamePasswordCredentials} into the default {@link CredentialsProvider}.
     * Returns the generated credential id for the registered credentials.
     */
    public static String registerUsernamePasswordCredentials(ModelObject context, String username, String password) throws Exception {
        String credentialsId = UUID.randomUUID().toString();
        setUsernamePasswordCredentials(context, credentialsId, username, password);
        return credentialsId;
    }

    /**
     * Registers the given value as a {@link UsernamePasswordCredentials} into the default {@link CredentialsProvider} using the
     * specified credentials id.
     */
    public static void setUsernamePasswordCredentials(ModelObject context, String credentialsId, String username, String password) throws Exception {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, null, username, password);
        CredentialsProvider.lookupStores(context).iterator().next().addCredentials(Domain.global(), creds);
    }
}
