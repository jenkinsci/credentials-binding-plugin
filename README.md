# Jenkins Credentials Binding Plugin
Allows credentials to be bound to environment variables for use from
miscellaneous build steps.

You may have a keystore for jarsigner, a list of passwords, or other
confidential files or strings which you want to be used by a job but
which should not be kept in its SCM, or even visible from its
config.xml. Saving these files on the server and referring to them by
absolute path requires you to have a server login, and does not work on
slaves. This plugin gives you an easy way to package up all a job’s
secret files and passwords and access them using a single environment
variable during the build.

To use, first go to the Credentials link and add items of type *Secret
file* and/or *Secret text*. Now in a freestyle job, check the box *Use
secret text(s) or file(s)* and add some variable bindings which will use
your credentials. The resulting environment variables can be accessed
from shell script build steps and so on. (You probably want to start any
shell script with `set +x`, or batch script with `@echo off`.
[JENKINS-14731](https://issues.jenkins-ci.org/browse/JENKINS-14731)).

For more details of how this works, check the [Injecting Secrets into
Jenkins Build
Jobs](https://cloudbees.zendesk.com/hc/en-us/articles/203802500-Injecting-Secrets-into-Jenkins-Build-Jobs)
article at CloudBees.

From a Pipeline job, define your credentials, then check *Snippet
Generator* for a syntax example of the `withCredentials` step. Any
secrets in the build log will be masked automatically.

A typical example of a username password type credential (example from
here) would look like: 

```groovy
withCredentials([usernamePassword(credentialsId: 'amazon', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
  // available as an env variable, but will be masked if you try to print it out any which way
  // note: single quotes prevent Groovy interpolation; expansion is by Bourne Shell, which is what you want
  sh 'echo $PASSWORD'
  // also available as a Groovy variable
  echo USERNAME
  // or inside double quotes for string interpolation
  echo "username is $USERNAME"
}
```

## Development
Start the local Jenkins instance: `mvn hpi:run`

## How to install
Run `mvn clean package` to create the plugin .hpi file.

To install:
1. copy the resulting ./target/credentials-binding.hpi file to the $JENKINS_HOME/plugins directory. Don't forget to restart Jenkins afterwards.
1. or use the plugin management console (http://example.com:8080/pluginManager/advanced) to upload the hpi file. You have to restart Jenkins in order to find the pluing in the installed plugins list.


## Plugin releases
`mvn release:prepare release:perform -B`

***
[Changelog](CHANGELOG.md)

License: [MIT](LICENSE)
