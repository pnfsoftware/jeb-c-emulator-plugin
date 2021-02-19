# JEB C Emulator

Plugin to emulate JEB's decompiled C code:

- It was originally built to analyze an heavily obfuscated crackme executable dubbed MarsAnalytica (see [companion blog](https://www.pnfsoftware.com/blog/traveling-around-mars-with-c-emulation/))

- Emulator has some strong limitations and should serve primarily as an example of what is doable with JEB decompiled C code (see SimpleCEmulator.java for known limitations)

- Emulator can be extended by adding specific logic in a class inheriting from SimpleCEmulator (see for example MarsAnalyticaCEmulator.java)

- /data repository contains an extract of MArsAnalytica's stack machine trace, and python scripts to replay it with symbols rather than concrete input, and to solve it using Z3

## Running it

### JEB's UI

- You need JEB version 4.0 or above
- Copy emulator Jar from the out/ folder to your JEB's coreplugins/ folder
- In JEB UI, File > Plugins > Execute an Engines Plugin > CEmulator

### CLI

- The plugin comes with a headless client, made to gather long emulator runs, with the ability to provide heap/stack memory dumps as starting point; see HeadlessClient.java for possible arguments

- To run headless client: java -cp CEmulatorPlugin-1.0.0.jar;[JEB INSTALL FOLDER]\bin\app\jeb.jar;. com.pnf.plugin.cemulator.HeadlessClient [ARGUMENTS]

## References 

- [Traveling Around Mars With C Emulation](https://www.pnfsoftware.com/blog/traveling-around-mars-with-c-emulation/) 

- [API Documentation](https://www.pnfsoftware.com/jeb/apidoc/reference/packages.html)
