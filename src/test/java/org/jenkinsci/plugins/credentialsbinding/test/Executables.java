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

package org.jenkinsci.plugins.credentialsbinding.test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import org.apache.commons.io.IOUtils;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

public class Executables {
    private static final String LOCATOR = Functions.isWindows() ? "where.exe" : "which";

    public static @CheckForNull
    String getPathToExecutable(@NonNull String executable) {
        try {
            Process process = new ProcessBuilder(LOCATOR, executable).start();
            List<String> output = IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
            if (process.waitFor() != 0) {
                return null;
            }
            return output.isEmpty() ? null : output.get(0);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    public static Matcher<String> executable() {
        return new CustomTypeSafeMatcher<String>("executable") {
            @Override
            protected boolean matchesSafely(String item) {
                try {
                    return new ProcessBuilder(LOCATOR, item).start().waitFor() == 0;
                } catch (InterruptedException | IOException e) {
                    return false;
                }
            }
        };
    }
}
