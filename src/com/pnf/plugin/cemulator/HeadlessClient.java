package com.pnf.plugin.cemulator;

import java.io.File;
import java.io.IOException;

import com.pnf.plugin.cemulator.EmulatorState.MemoryDump;
import com.pnfsoftware.jeb.client.HeadlessClientContext;
import com.pnfsoftware.jeb.core.Artifact;
import com.pnfsoftware.jeb.core.IEnginesContext;
import com.pnfsoftware.jeb.core.IRuntimeProject;
import com.pnfsoftware.jeb.core.exceptions.JebException;
import com.pnfsoftware.jeb.core.input.FileInput;
import com.pnfsoftware.jeb.util.base.Assert;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;

/**
 * Headless JEB client to run the companion {@link CEmulatorPlugin}
 * <p>
 * Refer to <a href="https://github.com/pnfsoftware/jeb-template-plugin">jeb-template-plugin</a> for
 * a more complete example of a headless client.
 * 
 * @author Joan Calvet
 *
 */
public class HeadlessClient {
    private static final ILogger logger = GlobalLog.getLogger(HeadlessClient.class);

    // arguments
    static File targetExecutablePath = null;
    static Long targetRoutineAddress = null;
    static File logPath = null;
    static Long stackBaseAddress = null;
    static File stackDump = null;
    static Long stackBasePointer = null;
    static Long heapBaseAddress = null;
    static File heapDump = null;

    public static void main(String[] args) throws JebException, IOException {
        HeadlessClientContext client = new HeadlessClientContext() {
            @Override
            public boolean isDevelopmentMode() {
                return true;
            }
        };

        parseArguments(args);

        // initialize and start the client
        client.initialize(args);
        client.start();

        IEnginesContext engctx = client.getEnginesContext();

        // process target file
        IRuntimeProject prj = engctx.loadProject("ProjectTest");
        prj.processArtifact(new Artifact(targetExecutablePath.getName(), new FileInput(targetExecutablePath)));

        // execute plugin
        try {
            CEmulatorPlugin plugin;
            if(stackDump != null && heapDump != null) {
                plugin = new CEmulatorPlugin(targetRoutineAddress,
                        new MemoryDump(stackBaseAddress, stackDump, stackBasePointer),
                        new MemoryDump(heapBaseAddress, heapDump));
            }
            else {
                plugin = new CEmulatorPlugin(targetRoutineAddress);
            }
            if(logPath != null) {
                plugin.setLogFile(logPath);
            }
            plugin.execute(client.getEnginesContext());
        }
        catch(Exception e) {
            logger.catching(e);
        }

        client.stop();
    }

    private static void parseArguments(String[] args) {
        for(int i = 0; i < args.length; i += 2) {
            if(args[i].equals("--stack-dump")) {
                stackDump = new File(args[i + 1]);
                Assert.a(stackDump.isFile(), "cannot find stack-dump");
            }
            else if(args[i].equals("--stack-base-adr")) {
                stackBaseAddress = Long.decode(args[i + 1]);
            }
            else if(args[i].equals("--stack-base-ptr")) {
                stackBasePointer = Long.decode(args[i + 1]);
            }
            else if(args[i].equals("--heap-dump")) {
                heapDump = new File(args[i + 1]);
                Assert.a(heapDump.isFile(), "cannot find heap-dump");
            }
            else if(args[i].equals("--heap-base-adr")) {
                heapBaseAddress = Long.decode(args[i + 1]);
            }
            else if(args[i].equals("--target")) {
                targetExecutablePath = new File(args[i + 1]);
                Assert.a(targetExecutablePath.isFile(), "cannot find target exec");
            }
            else if(args[i].equals("--rtn")) {
                targetRoutineAddress = Long.decode(args[i + 1]);
            }
            else if(args[i].equals("--log")) {
                logPath = new File(args[i + 1]);
                Assert.a(logPath.isFile(), "cannot find log file");
            }
            else {
                logger.i("> ERROR: invalid argument (%s)", args[i]);
                usage();
                return;
            }
        }
        if(targetExecutablePath == null || targetRoutineAddress == null) {
            logger.i("> ERROR: missing arguments");
            usage();
            return;
        }
    }

    private static void usage() {
        //@formatter:off
        logger.i("Usage: " + 
                "--target path                  : path to executable file to emulate" +
                "--rtn 0xAAAAAAAA               : address of first routine to emulate" +
                "--log path                     : path to logfile (optional)" +
                "--stack-dump path              : path to stack dump file (optional)" +
                "--stack-base-adr 0xAAAAAAAA    : stack dump base address (optional)" +
                "--stack-base-ptr 0xAAAAAAAA    : stack base pointer (optional)" +
                "--heap-dump path               : path to heap dump file  (optional)" +
                "--heap-base-adr 0xAAAAAAAA           : heap dump base address (optional)");
        //@formatter:on
    }
}
