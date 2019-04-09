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

import hudson.Extension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

@Extension
@Restricted(NoExternalUse.class)
public class BatchPatternMaskerProvider extends AbstractShellPatternMaskerProvider {

    // adapted from:
    // https://blogs.msdn.microsoft.com/twistylittlepassagesallalike/2011/04/23/everyone-quotes-command-line-arguments-the-wrong-way/
    // also from WindowsUtil in Jenkins

    private static final Pattern CMD_METACHARS = Pattern.compile("[()%!^\"<>&|]");
    private static final Pattern QUOTED_CHARS = Pattern.compile("(\\^)(\\^?)");

    @Override
    @Nonnull String getQuotedForm(@Nonnull String input) {
        int end = input.length();
        StringBuilder sb = new StringBuilder(end);
        for (int i = 0; i < end; i++) {
            int nrBackslashes = 0;
            while (i < end && input.charAt(i) == '\\') {
                i++;
                nrBackslashes++;
            }

            if (i == end) {
                // backslashes at the end of the argument must be escaped so the terminate quote isn't
                nrBackslashes = nrBackslashes * 2;
            } else if (input.charAt(i) == '"') {
                // backslashes preceding a quote all need to be escaped along with the quote
                nrBackslashes = nrBackslashes * 2 + 1;
            }
            // else backslashes have no special meaning and don't need to be escaped here

            for (int j = 0; j < nrBackslashes; j++) {
                sb.append('\\');
            }

            if (i < end) {
                sb.append(input.charAt(i));
            }
        }
        return CMD_METACHARS.matcher(sb).replaceAll("^$0");
    }

    @Override
    @Nonnull String surroundWithQuotes(@Nonnull String input) {
        return '"' + input + '"';
    }

    @Override
    @Nonnull String getUnquotedForm(@Nonnull String input) {
        return QUOTED_CHARS.matcher(input).replaceAll("$2");
    }
}
