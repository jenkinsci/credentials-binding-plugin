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

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Creates regular expressions to match encoded forms of secrets in logs.
 * These are typically implemented to handle various shell quoting algorithms (sometimes confused with escaping) to
 * pass literal string values to an interpreter.
 */
public interface SecretPatternFactory extends ExtensionPoint {

    /**
     * Returns a collection of alternative forms the given input may show up as in logs.
     * Note that these patterns must embed their flags in the pattern rather than as parameters to Pattern.
     */
    @Nonnull Collection<Pattern> getSecretPatterns(@Nonnull String input);

    /**
     * Returns all SecretPatternFactory extensions known at runtime.
     */
    static @Nonnull ExtensionList<SecretPatternFactory> all() {
        return ExtensionList.lookup(SecretPatternFactory.class);
    }

    /**
     * Composes {@link Pattern#compile(String)} and {@link Pattern#quote(String)} for convenience.
     */
    static @Nonnull Pattern quotedCompile(@Nonnull String input) {
        return Pattern.compile(Pattern.quote(input));
    }

}
