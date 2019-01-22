# JEB Back-End Plugin Template

Skeleton code to develop and test a JEB back-end plugin.

## Requirements

- A JEB license that supports back-end plugins (e.g. *JEB Pro* or *JEB Android*) version 3.1.1 or above.
- [Eclipse IDE](https://www.eclipse.org/downloads/packages/release/2018-12/r/eclipse-ide-java-developers)

## Getting Started

- Create a plugin folder and copy this repository: `git init MyPlugin && cd MyPlugin && git pull https://github.com/pnfsoftware/jeb-template-plugin`
- Define a JEB_HOME environment variable and initialize it to your JEB installation folder
- Run the create-eclipse-project script: it will create an Eclipse project, set up jeb.jar dependency and javadoc for in-IDE documentation and auto-completion
- Import the project into Eclipse (menu File, Import, Existing Projects into the Workspace, ...)
- Start implementing your plugin (entry-point class: SamplePlugin) and your Tester's testPlugin() method

## Deploying

- Adjust your plugin name and version in build.cmd/build.sh
- Execute the build script (Ant is required)
- Copy the resulting Jar from the out/ folder to your JEB's coreplugins/ folder

## Tutorials

- [How to write an IR optimizer plugin for JEB's native decompilation pipeline](https://www.pnfsoftware.com/blog/jeb-native-pipeline-ir-optimizers-part-2/)

## Reference

- [API Documentation](https://www.pnfsoftware.com/jeb/apidoc/reference/packages.html)
