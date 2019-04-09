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

import hudson.Functions;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.io.IOException;

/**
 * Hamcrest matcher for strings to check if the given command is executable.
 * This uses {@code which} or {@code where.exe} to determine this depending on operating system.
 */
public class ExecutableExists extends TypeSafeMatcher<String> {

    public static Matcher<String> executable() {
        return new ExecutableExists();
    }

    private static final String LOCATOR = Functions.isWindows() ? "where.exe" : "which";

    @Override
    protected boolean matchesSafely(String item) {
        try {
            return new ProcessBuilder(LOCATOR, item).start().waitFor() == 0;
        } catch (InterruptedException | IOException e) {
            return false;
        }
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("executable");
    }
}
