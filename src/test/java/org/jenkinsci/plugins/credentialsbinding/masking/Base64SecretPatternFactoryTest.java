package org.jenkinsci.plugins.credentialsbinding.masking;

import static org.jenkinsci.plugins.credentialsbinding.test.Executables.isExecutable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.Functions;
import org.jenkinsci.plugins.credentialsbinding.test.CredentialsTestUtil;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Base64SecretPatternFactoryTest {

    private static final String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void base64SecretsAreMaskedInLogs() throws Exception {
        WorkflowJob project = r.createProject(WorkflowJob.class);
        String credentialsId = CredentialsTestUtil.registerUsernamePasswordCredentials(r.jenkins, "user", SAMPLE_PASSWORD);
        String script;

        if (Functions.isWindows()) {
            assumeTrue(isExecutable("powershell"));
            script =
                    """
                                powershell '''
                                  $secret = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$env:PASSWORD"))
                                  echo $secret
                                '''
                            """;
        } else {
            script =
                    """
                                sh '''
                                  echo -n $PASSWORD | base64
                                '''
                            """;
        }

        project.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  withCredentials([usernamePassword(credentialsId: '" + credentialsId + "', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {\n"
                        + script
                        + "  }\n"
                        + "}", true));

        WorkflowRun run = r.assertBuildStatusSuccess(project.scheduleBuild2(0));

        r.assertLogContains("****", run);
        r.assertLogNotContains(SAMPLE_PASSWORD, run);
    }
}