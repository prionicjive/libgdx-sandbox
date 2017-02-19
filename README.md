libgdx-sandbox
===============

A silly little LibGDX sandbox.

###How to get started

Clone the repo to your desktop or download and extract the master [ZIP](https://github.com/innerlogic/libgdx-sandbox/archive/master.zip) file.

#### Setup Environment Variables
Certain environment variables need to be set. Refer to the [LibGDX wiki](https://github.com/libgdx/libgdx/wiki/Setting-up-your-Development-Environment-%28Eclipse%2C-Intellij-IDEA%2C-NetBeans%29) for instructions on how to install all the necessary prerequisites.

  * `JAVA_HOME` (JDK installation directory)

#### Use Gradle for dependency management and build tasks

In order to manage dependencies and allow for other interesting build/development related tasks, [LibGDX has leveraged Gradle](https://github.com/libgdx/libgdx/wiki/Project-Setup-Gradle) as a potential tool. Using [Gradle](http://www.gradle.org/) helps ensure that all dependencies are kept up-to-date and prevents the need for JARs and IDE-related files to be in source control.  This also allows developers to work with the source in whatever environment they feel most comfortable with. For more information on Gradle and how LibGDX leverages it, visit the  [LibGDX wiki](https://github.com/libgdx/libgdx/wiki/Project-Setup-Gradle).

For purposes of this project, it is rather simple to pull in the needed dependencies so you (the developer) don't have to worry about them later :-) From the project directory in the command line / terminal, run the following command:

    gradlew clean

To build and run the desktop project, run the following command:

    gradlew desktop:run

Gradle can do many build/development related tasks. Use one of the examples below to get more information from the command line:

    gradlew tasks
    gradlew desktop:tasks

Visit the [LibGDX wiki](https://github.com/libgdx/libgdx/wiki/Project-Setup-Gradle) for more information.

#### Generate IDE files for IntelliJ IDEA

LibGDX and Gradle make it very easy to generate / leverage files for your IDE of choice. In this case, IntelliJ IDEA is your only choice :D

    gradlew idea

Visit the [LibGDX wiki](https://github.com/libgdx/libgdx/wiki/Project-Setup-Gradle) for detailed instructions on setting up, building and debugging with [IntelliJ IDEA](https://github.com/libgdx/libgdx/wiki/Gradle-and-Intellij-IDEA) and from the [command line](https://github.com/libgdx/libgdx/wiki/Gradle-on-the-Commandline).
