package org.jenkinsci.plugins.credentialsbinding.masking;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.BatchFile;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper;
import org.jenkinsci.plugins.credentialsbinding.impl.StringBinding;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

public class DollarSecretPatternFactoryTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-24805")
    @Test
    public void maskingFreeStyleSecrets() throws Exception {
        String firstCredentialsId = "creds_1";
        String firstPassword = "a$build";
        StringCredentialsImpl firstCreds = new StringCredentialsImpl(CredentialsScope.GLOBAL, firstCredentialsId, "sample1", Secret.fromString(firstPassword));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), firstCreds);

        String secondCredentialsId = "creds_2";
        String secondPassword = "a$$b";
        StringCredentialsImpl secondCreds = new StringCredentialsImpl(CredentialsScope.GLOBAL, secondCredentialsId, "sample2", Secret.fromString(secondPassword));

        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), secondCreds);

        SecretBuildWrapper wrapper = new SecretBuildWrapper(Arrays.asList(new StringBinding("PASS_1", firstCredentialsId),
                new StringBinding("PASS_2", secondCredentialsId)));

        FreeStyleProject project = r.createFreeStyleProject();

        project.setConcurrentBuild(true);
        project.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %PASS_1%") : new Shell("echo \"$PASS_1\""));
        project.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %PASS_2%") : new Shell("echo \"$PASS_2\""));
        project.getBuildersList().add(new Maven("$PASS_1 $PASS_2", "default"));
        project.getBuildWrappersList().add(wrapper);

        r.configRoundtrip((Item)project);

        QueueTaskFuture<FreeStyleBuild> future = project.scheduleBuild2(0);
        FreeStyleBuild build = future.get();
        r.assertLogNotContains(firstPassword, build);
        r.assertLogNotContains(firstPassword.replace("$", "$$"), build);
        r.assertLogNotContains(secondPassword, build);
        r.assertLogNotContains(secondPassword.replace("$", "$$"), build);
        r.assertLogContains("****", build);
    }

}
