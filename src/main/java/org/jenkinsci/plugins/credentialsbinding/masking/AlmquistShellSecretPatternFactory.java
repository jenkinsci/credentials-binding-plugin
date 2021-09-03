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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Collection;
import java.util.Collections;

/**
 * Almquist shell, aka ash, is the default shell in Alpine distribution.
 */
@Extension
@Restricted(NoExternalUse.class)
public class AlmquistShellSecretPatternFactory implements SecretPatternFactory {

    private static final char SINGLE_QUOTE = '\'';
    private static final String START_FRAGMENT = "'\"";
    private static final String END_FRAGMENT = "\"'";

    private @NonNull String getQuotedForm(@NonNull String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == SINGLE_QUOTE) {
                // a single quote is replaced by '"'"' which is the concatenation of
                // '[part before...]' + "'" + '[...part after]'
                // if there are multiple single quotes successively, they are included in the double quotes together
                sb.append(START_FRAGMENT);
                // the first one, at index i
                sb.append(SINGLE_QUOTE);
                for (int j = i + 1; j < input.length() && input.charAt(j) == SINGLE_QUOTE; j++) {
                    sb.append(SINGLE_QUOTE);
                    i++;
                }
                sb.append(END_FRAGMENT);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @NonNull
    @Override
    public Collection<String> getEncodedForms(@NonNull String input) {
        return Collections.singleton(getQuotedForm(input));
    }
}
