## Changelog

### Version 1.19 (2019-05-31)

-   [JENKINS-42950](https://issues.jenkins-ci.org/browse/JENKINS-42950) -
	Expanded secret masking to
    include some variants commonly produced by shells when
    metacharacters are involved.

### Version 1.18 (2019-02-25)

-   [JENKINS-42950](https://issues.jenkins-ci.org/browse/JENKINS-42950) -
	Better documentation about limitations and best practices.
-   [JENKINS-49337](https://issues.jenkins-ci.org/browse/JENKINS-49337) -
	Avoiding blocking the Pipeline
    interpreter during block cleanup even if Remoting hangs.
-   Avoid printing a stack trace when a `credentialsId`  is not found.

### Version 1.17 (2018-10-29)

-   Improved stream flush behavior in light
    of [JEP-210](https://jenkins.io/jep/210).

### Version 1.16 (Mar 19, 2018)

-   [JENKINS-49535](https://issues.jenkins-ci.org/browse/JENKINS-49535)
-   Improving help text in light of [JENKINS-50242](https://issues.jenkins-ci.org/browse/JENKINS-50242).

### Version 1.15 (Feb 05, 2018)

-   [Fix security issue](https://jenkins.io/security/advisory/2018-02-05/)

### Version 1.14 (Jan 17, 2018)

-	[JENKINS-37871](https://issues.jenkins-ci.org/browse/JENKINS-37871) -
    Adjusted order of freestyle build
    wrapper relative to other wrappers.
-	[JENKINS-48118](https://issues.jenkins-ci.org/browse/JENKINS-48118) -
	Metadata fixes useful for `plugin-compat-tester`.

### Version 1.13 (Aug 08, 2017)

-   [JENKINS-28399](https://issues.jenkins-ci.org/browse/JENKINS-28399) - Binding
    type for SSH user private key credentials.
-   [JENKINS-41760](https://issues.jenkins-ci.org/browse/JENKINS-41760) - Corrupted
    output when no credentials were specified, or a supposed secret was
    in fact blank.
-   [JENKINS-43199](https://issues.jenkins-ci.org/browse/JENKINS-43199) File
    descriptor leak when using build wrapper.

### Version 1.12 (Jun 15, 2017)

-   Binding type for certificate credentials.
-   New APIs `AbstractOnDiskBinding` and `UnbindableDir`.

### Version 1.11 (Mar 30, 2017)

-   [JENKINS-42999](https://issues.jenkins-ci.org/browse/JENKINS-42999) -
    Allow non-file-based credentials to be used from `withCredentials`
    outside a `node` block.
-   Japanese localization.

### Version 1.10 (Nov 07, 2016)

-   [JENKINS-24805](https://issues.jenkins-ci.org/browse/JENKINS-24805) -
    Mask passwords in freestyle builds, not just in Pipeline builds.
-   Masking did not work correctly if some secrets were a substring of
    others.
-   [JENKINS-38831](https://issues.jenkins-ci.org/browse/JENKINS-38831) -
    Track credentials usage.
-   Adding symbols to binding types for better readability in Pipeline
    (and probably also Job DSL).

### Version 1.9 (Aug 19, 2016)

-   [JENKINS-37541](https://issues.jenkins-ci.org/browse/JENKINS-37541) prevent
    NPE while reading back SecretBuildWrapper
-   Migrate to new parent pom 

### Version 1.8 (Jun 10, 2016)

-   [JENKINS-35490](https://issues.jenkins-ci.org/browse/JENKINS-35490) fix
    regression introduced in JENKINS-27387 fix
-   [JENKINS-35095](https://issues.jenkins-ci.org/browse/JENKINS-35095) migrate
    to new parent pom 

### Version 1.7 (Mar 03, 2016)

-   [JENKINS-32943](https://issues.jenkins-ci.org/browse/JENKINS-32943)/[JENKINS-27152](https://issues.jenkins-ci.org/browse/JENKINS-27152) -
    Use a standardized temporary directory understood by Docker
    Pipeline.
-   [JENKINS-27387](https://issues.jenkins-ci.org/browse/JENKINS-27387) -
    Failure to generate snippet in a Pipeline configuration page.

### Version 1.6 (Oct 16, 2015)

-   [JENKINS-30941](https://issues.jenkins-ci.org/browse/JENKINS-30941) -
    Fixed regression in 1.5 affecting ZIP file bindings.
-   Resource leak potentially affecting ZIP file bindings.
-   [JENKINS-30326](https://issues.jenkins-ci.org/browse/JENKINS-30326) -
    updated dependency on credentials plugin to 1.23

### Version 1.5 (Aug 06, 2015)

-   [JENKINS-29255](https://issues.jenkins-ci.org/browse/JENKINS-29255) -
    Set restrictive file permission on *Secret File* binding, to make it
    easier to use an SSH private key this way.

### Version 1.4 (Apr 01, 2015)

-   Updated to Jenkins 1.596.1 and Workflow 1.5.
-   [JENKINS-27486](https://issues.jenkins-ci.org/browse/JENKINS-27486) -
    `withCredentials` step should mask any passwords accidentally
    printed to the log.
-   [JENKINS-27631](https://issues.jenkins-ci.org/browse/JENKINS-27631) -
    `withCredentials` step should not store passwords even temporarily
    in `program.dat` in the build directory.
-   [JENKINS-27389](https://issues.jenkins-ci.org/browse/JENKINS-27389) -
    `withCredentials` step was exposing variables to external processes
    but not to Groovy code using `env.PASSWORD` syntax.
-   Improved help for `withCredentials`.
-   Improved error diagnostics for `withCredentials`.

### Version 1.3 (Jan 20, 2015)

-   [JENKINS-26051](https://issues.jenkins-ci.org/browse/JENKINS-26051) -
    Added `withCredentials` Workflow step.
    [Blog](http://developer-blog.cloudbees.com/2015/01/workflow-integration-for-credentials.html).
-   [JENKINS-23468](https://issues.jenkins-ci.org/browse/JENKINS-23468) -
    Allowed username & password to be bound to separate variables.
-   SPI changes to permit the above two features.

### Version 1.2 (Oct 07, 2014)

-   SECURITY-158 fix.

### Version 1.1 (Aug 11, 2014)

-   Add support for parameterized credentials (from credentials plugin
    1.16.1)

### Version 1.0 (Jun 16, 2014)

First general release.
-   Supporting username/password credentials.
-   Marking added environment variables as “sensitive”, so other code
    showing them should display the values masked.

### Version 1.0 beta 1 (Oct 01, 2013)

-   Factored out from [Plain Credentials Plugin](https://plugins.jenkins.io/plain-credentials/).
