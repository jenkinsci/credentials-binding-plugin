package org.jenkinsci.plugins.credentialsbinding.impl;

import hudson.AbortException;

public class CredentialNotFoundException extends AbortException {
    public CredentialNotFoundException(String message) {
        super(message);
    }
}
