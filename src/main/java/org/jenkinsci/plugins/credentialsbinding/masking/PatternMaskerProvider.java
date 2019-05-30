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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Quotes strings according to quotation rules of various programs. Sometimes confused with escaping, quoting is done
 * to pass literal strings in a shell or other interpreter.
 *
 */
public interface PatternMaskerProvider extends ExtensionPoint {

    /**
     * Returns a collection of alternative forms as regular expressions the given input may show up as in logs. These
     * patterns should be appropriately {@linkplain Pattern#quote(String) quoted} for matching literal patterns as
     * the resulting strings are formed into a regular expression.
     */
    @Nonnull Collection<String> getAlternativeForms(@Nonnull String input);

    /**
     * Returns all PatternMaskerProvider extensions known at runtime.
     */
    static @Nonnull ExtensionList<PatternMaskerProvider> all() {
        return ExtensionList.lookup(PatternMaskerProvider.class);
    }

    /**
     * Returns a collection of all alternative form regular expressions for the given input using all PatternMaskerProvider
     * extensions.
     */
    @Restricted(NoExternalUse.class)
    static @Nonnull Collection<String> getAllAlternateForms(@Nonnull String input) {
        Collection<String> alternateForms = new HashSet<>();
        for (PatternMaskerProvider provider : all()) {
            alternateForms.addAll(provider.getAlternativeForms(input));
        }
        return alternateForms;
    }

    /**
     * Constructs a regular expression to match against all known forms that the given collection of input strings may
     * appear. This pattern is optimized such that longer masks are checked before shorter masks. By doing so, this
     * makes inputs that are substrings of other inputs be masked as the longer input, though there is no guarantee
     * this will work in every possible situation. Another consequence of this ordering is that inputs that require
     * quoting will be masked before a substring of the input was matched, thus avoiding leakage of information.
     * For example, {@code bash -x} will only quote arguments echoed when necessary. To avoid leaking the presence or
     * absence of quoting, the longer form is masked.
     */
    @Restricted(NoExternalUse.class)
    static @Nonnull Pattern getMaskingPattern(@Nonnull Collection<String> inputs) {
        Collection<String> patterns = new TreeSet<>(Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo));
        for (String input : inputs) {
            if (input.isEmpty()) {
                continue;
            }
            patterns.add(Pattern.quote(input));
            patterns.addAll(getAllAlternateForms(input));
        }
        return Pattern.compile(String.join("|", patterns));
    }
}
