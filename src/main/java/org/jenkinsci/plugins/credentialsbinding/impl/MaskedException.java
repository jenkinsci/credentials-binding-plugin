package org.jenkinsci.plugins.credentialsbinding.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class MaskedException extends Exception {
    private static final long serialVersionUID = 1L;

    static Throwable of(@NonNull Throwable unmasked, Pattern pattern) {
        return of(unmasked, new HashSet<>(), pattern);
    }

    private static Throwable of(@NonNull Throwable unmasked, @NonNull Set<Throwable> visited, Pattern pattern) {
        if (!visited.add(unmasked)) {
            return new Exception("cycle");
        }
        var text = Functions.printThrowable(unmasked);
        if (pattern.matcher(text).find()) {
            var masked = new MaskedException(pattern.matcher(Objects.requireNonNullElse(unmasked.getMessage(), "")).replaceAll("****"));
            masked.setStackTrace(unmasked.getStackTrace());
            var cause = unmasked.getCause();
            if (cause != null) {
                masked.initCause(of(cause, visited, pattern));
            }
            for (var suppressed : unmasked.getSuppressed()) {
                masked.addSuppressed(of(suppressed, visited, pattern));
            }
            return masked;
        } else {
            return unmasked;
        }
    }

    private MaskedException(String message) {
        super(message);
    }

}
