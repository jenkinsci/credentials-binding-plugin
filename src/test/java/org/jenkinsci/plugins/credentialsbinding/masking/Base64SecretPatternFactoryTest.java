package org.jenkinsci.plugins.credentialsbinding.masking;

import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.credentialsbinding.test.Executables.executable;
import static org.junit.Assume.assumeThat;

import hudson.Functions;
import org.jenkinsci.plugins.credentialsbinding.test.CredentialsTestUtil;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class Base64SecretPatternFactoryTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static final String SAMPLE_PASSWORD = "}#T14'GAz&H!{$U_";

    @Test
    public void base64SecretsAreMaskedInLogs() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        String credentialsId = CredentialsTestUtil.registerUsernamePasswordCredentials(j.jenkins, "user", SAMPLE_PASSWORD);
        String script;

        if (Functions.isWindows()) {
            assumeThat("powershell", is(executable()));
            script =
                    "    powershell '''\n"
                            + "      $secret = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(\"$env:PASSWORD\"))\n"
                            + "      echo $secret\n"
                            + "    '''\n";
        } else {
            script =
                    "    sh '''\n"
                            + "      echo -n $PASSWORD | base64\n"
                            + "    '''\n";
        }

        project.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  withCredentials([usernamePassword(credentialsId: '" + credentialsId + "', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {\n"
                        + script
                        + "  }\n"
                        + "}", true));

        WorkflowRun run = j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        j.assertLogContains("****", run);
        j.assertLogNotContains(SAMPLE_PASSWORD, run);
    }
}