/*
 * The MIT License
 *
 * Copyright 2013 jglick.
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

package org.jenkinsci.plugins.credentialsbinding;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;

import java.util.Collections;

import org.kohsuke.stapler.AncestorInPath;

/**
 * Describes a {@link MultiBinding} kind.
 * @param <C> type of credentials to be bound
 */
public abstract class BindingDescriptor<C extends StandardCredentials> extends Descriptor<MultiBinding<C>> {

    protected abstract Class<C> type();

    /**
     * Determines whether this {@link MultiBinding} needs a workspace to evaluate.
     */
    public boolean requiresWorkspace() {
        return true;
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item owner) {
        if (owner == null || !owner.hasPermission(Item.CONFIGURE)) {
            return new ListBoxModel();
        }
        // when configuring the job, you only want those credentials that are available to ACL.SYSTEM selectable
        // as we cannot select from a user's credentials unless they are the only user submitting the build
        // (which we cannot assume) thus ACL.SYSTEM is correct here.
        return new Model().withAll(CredentialsProvider.lookupCredentials(type(), owner, ACL.SYSTEM, Collections.emptyList()));
    }

    private final class Model extends AbstractIdCredentialsListBoxModel<Model,C> {

        @Override protected String describe(C c) {
            return CredentialsNameProvider.name(c);
        }

    }

}
