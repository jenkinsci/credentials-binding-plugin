package org.jenkinsci.plugins.credentialsbinding.impl;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class CredentialNotFoundException extends IOException {
    public CredentialNotFoundException(String message) {
        super(message);
    }

    public CredentialNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public CredentialNotFoundException(Throwable cause) {
        super(cause);
    }
}
