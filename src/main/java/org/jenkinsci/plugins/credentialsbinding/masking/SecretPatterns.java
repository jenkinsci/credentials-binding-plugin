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

package org.jenkinsci.plugins.credentialsbinding.masking;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.LineTransformationOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SecretPatterns {

    private static final Comparator<String> BY_LENGTH_DESCENDING =
            Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo);

    /**
     * Constructs a regular expression to match against all known forms that the given collection of input strings may
     * appear. This pattern is optimized such that longer masks are checked before shorter masks. By doing so, this
     * makes inputs that are substrings of other inputs be masked as the longer input, though there is no guarantee
     * this will work in every possible situation. Another consequence of this ordering is that inputs that require
     * quoting will be masked before a substring of the input was matched, thus avoiding leakage of information.
     * For example, {@code bash -x} will only quote arguments echoed when necessary. To avoid leaking the presence or
     * absence of quoting, the longer form is masked.
     */
    public static @NonNull Pattern getAggregateSecretPattern(@NonNull Collection<String> inputs) {
        String pattern = inputs.stream()
                .filter(input -> !input.isEmpty())
                .flatMap(input ->
                        SecretPatternFactory.all().stream().flatMap(factory ->
                                factory.getEncodedForms(input).stream()))
                .sorted(BY_LENGTH_DESCENDING)
                .distinct()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile(pattern);
    }

    /**
     * Delegating output stream that masks occurrences of a set of secrets.
     */
    public static class MaskingOutputStream extends LineTransformationOutputStream.Delegating {

        private final @CheckForNull Pattern secretPattern;
        private final @NonNull String charsetName;

        /**
         * @param out the base output stream which will not be sent secrets
         * @param secretPattern the result of {@link #getAggregateSecretPattern}, or null to just skip masking
         * @param charsetName the character set to detect strings
         */
        public MaskingOutputStream(@NonNull OutputStream out, @CheckForNull Pattern secretPattern, @NonNull String charsetName) {
            super(out);
            this.secretPattern = secretPattern;
            this.charsetName = charsetName;
        }

        @Override protected void eol(byte[] b, int len) throws IOException {
            if (secretPattern != null && !secretPattern.toString().isEmpty()) {
                Matcher m = secretPattern.matcher(new String(b, 0, len, charsetName));
                if (m.find()) {
                    out.write(m.replaceAll("****").getBytes(charsetName));
                } else {
                    // Avoid byte → char → byte conversion unless we are actually doing something.
                    out.write(b, 0, len);
                }
            } else {
                // Avoid byte → char → byte conversion unless we are actually doing something.
                out.write(b, 0, len);
            }
        }

    }

    private SecretPatterns() {}

}
