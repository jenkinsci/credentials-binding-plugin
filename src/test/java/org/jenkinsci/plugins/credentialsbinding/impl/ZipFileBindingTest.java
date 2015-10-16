/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import java.io.File;
import java.io.InputStream;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;

import hudson.Functions;
import hudson.model.FileParameterValue.FileItemImpl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class ZipFileBindingTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Issue("JENKINS-30941")
    @Test
    public void cleanUpSucceeds() throws Exception {
        /** Issue was just present on Linux not windows - but the test will run on both */

        final String credentialsId = "zipfile";

        /* do the dance to get a simple zip file into jenkins */
        InputStream zipStream = this.getClass().getResourceAsStream("a.zip");
        try {
            assertThat(zipStream, is(not(nullValue())));
            File zip = tmp.newFile("a.zip");
            FileUtils.copyInputStreamToFile(zipStream, zip);
            FileItem fi = new FileItemImpl(zip);
            FileCredentialsImpl fc = new FileCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "Just a zip file", fi, fi.getName(), null);
            CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), fc);
        }
        finally {
            IOUtils.closeQuietly(zipStream);
            zipStream = null;
        }


        final String unixFile = "/dir/testfile.txt";
        final String winFile = unixFile.replace("/", "\\\\"); /* two \\ as we escape the code and then escape for the script */
        //if this file does not have a line ending then the text is not echoed to the log.
        // fixed in workflow 1.11+ (which is not released at the time of writing)
        final String contents = "Test of ZipFileBinding\n";
        
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(""
                                              + "node {\n"
                                              + "  withCredentials([[$class: 'ZipFileBinding', credentialsId: '"+ credentialsId +"', variable: 'ziploc']]) {\n"
                                              + (Functions.isWindows() 
                                                      ? "    bat 'type %ziploc%"+ winFile + "'\n"
                                                      : "    sh 'cat ${ziploc}" + unixFile + "'\n" )
                                              + "    def text = readFile encoding: 'UTF-8', file: \"${env.ziploc}" + unixFile + "\"\n"
                                              + "    if (!text.equals('''" + contents + "''')) {\n"
                                              + "      error ('incorrect details from zip file')\n"
                                              + "    }\n"
                                              + "  }\n"
                                              + "}\n"
                                              , true));

        WorkflowRun run = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(contents, run);
    }
}
