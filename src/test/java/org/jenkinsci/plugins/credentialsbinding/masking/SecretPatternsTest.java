/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SecretPatternsTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;
    @RegisterExtension
    private final InboundAgentExtension agents = new InboundAgentExtension();
    private boolean useWatching;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        useWatching = DurableTaskStep.USE_WATCHING;
    }

    @AfterEach
    void afterEach() {
        DurableTaskStep.USE_WATCHING = useWatching;
    }

    @Issue("SECURITY-3075")
    @Test
    void secretPatternFactoriesRetrievedFromAgent() throws Exception {
        DurableTaskStep.USE_WATCHING = true;
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('remote') {def msg = 'echo do not look at s3cr3t please'; if (isUnix()) {sh msg} else {bat msg}}", true));
        agents.createAgent(r, "remote");
        try {
            WorkflowRun b = r.waitForCompletion(p.scheduleBuild2(0).waitForStart());
            r.assertLogContains(BadMasker.class.getName(), b);
            /* Not currently ensured:
            r.assertLogNotContains("s3cr3t", b);
            */
        } finally {
            agents.stop("remote");
        }
    }

    public static final class BadMasker extends TaskListenerDecorator {

        @Override
        public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
            Pattern pattern = SecretPatterns.getAggregateSecretPattern(Set.of("s3cr3t"));
            return new SecretPatterns.MaskingOutputStream(logger, () -> pattern, "UTF-8");
        }

        @TestExtension("secretPatternFactoriesRetrievedFromAgent")
        public static final class Factory implements TaskListenerDecorator.Factory {

            @Override
            public TaskListenerDecorator of(FlowExecutionOwner owner) {
                return new BadMasker();
            }
        }
    }

}
