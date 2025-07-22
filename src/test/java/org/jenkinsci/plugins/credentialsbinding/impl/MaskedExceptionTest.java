/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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
package org.jenkinsci.plugins.credentialsbinding.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class MaskedExceptionTest {
    @Issue("JENKINS-75914")
    @Test
    public void maskedExceptionIgnoresShortSecretsEmpty() throws Exception {
        Exception unmasked = new Exception("An empty pattern for exception mask");
        var masked = MaskedException.of(unmasked, Pattern.compile(""));
        assertThat(masked.getMessage(), not(containsString("****")));
    }

    @Issue("JENKINS-75914")
    @Test
    public void maskedExceptionIgnoresShortSecrets1Char() throws Exception {
        Exception unmasked = new Exception("1 character pattern for exception mask");
        var masked = MaskedException.of(unmasked, Pattern.compile("e"));
        assertThat(masked.getMessage(), not(containsString("****")));
    }

    @Issue("JENKINS-75914")
    @Test
    public void maskedExceptionIgnoresShortSecrets2Char() throws Exception {
        Exception unmasked = new Exception("2 character pattern for exception mask");
        var masked = MaskedException.of(unmasked, Pattern.compile("n "));
        assertThat(masked.getMessage(), not(containsString("****")));
    }

    @Issue("JENKINS-75914")
    @Test
    public void maskedExceptionIgnoresShortSecrets4Char() throws Exception {
        Exception unmasked = new Exception("4 character pattern for exception mask");
        var masked = MaskedException.of(unmasked, Pattern.compile("patt"));
        assertThat(masked.getMessage(), containsString("****"));
    }
}
