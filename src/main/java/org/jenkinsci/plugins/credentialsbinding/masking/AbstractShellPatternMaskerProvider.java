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

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Convenient base class for PatternMaskerProvider implementations for shells.
 */
abstract class AbstractShellPatternMaskerProvider implements PatternMaskerProvider {

    /**
     * Performs any quoting/escaping necessary for the input string to be able to be surrounded in quotes and passed
     * literally to a shell. This should not include the surrounding quotes.
     */
    abstract @Nonnull String getQuotedForm(@Nonnull String input);

    /**
     * Surrounds a string with the proper quote characters to make a quoted string for a shell.
     */
    abstract @Nonnull String surroundWithQuotes(@Nonnull String input);

    /**
     * Performs the inverse of {@link #getQuotedForm(String)}.
     */
    abstract @Nonnull String getUnquotedForm(@Nonnull String input);

    @Override
    public @Nonnull Collection<String> getAlternativeForms(@Nonnull String input) {
        Collection<String> patterns = new HashSet<>();
        String quotedForm = getQuotedForm(input);
        patterns.add(Pattern.quote(quotedForm));
        patterns.add(Pattern.quote(surroundWithQuotes(quotedForm)));
        patterns.add(Pattern.quote(getUnquotedForm(input)));
        return patterns;
    }
}
