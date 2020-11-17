package com.pnf.plugin.cemulator;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.pnf.plugin.cemulator.EmulatorState.MemoryDump;
import com.pnfsoftware.jeb.core.AbstractEnginesPlugin;
import com.pnfsoftware.jeb.core.BooleanOptionDefinition;
import com.pnfsoftware.jeb.core.IEnginesContext;
import com.pnfsoftware.jeb.core.ILiveArtifact;
import com.pnfsoftware.jeb.core.IOptionDefinition;
import com.pnfsoftware.jeb.core.IPluginInformation;
import com.pnfsoftware.jeb.core.IRuntimeProject;
import com.pnfsoftware.jeb.core.OptionDefinition;
import com.pnfsoftware.jeb.core.PluginInformation;
import com.pnfsoftware.jeb.core.Version;
import com.pnfsoftware.jeb.core.exceptions.JebRuntimeException;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.IUnit;
import com.pnfsoftware.jeb.core.units.UnitUtil;
import com.pnfsoftware.jeb.core.units.code.EntryPointDescription;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilerUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeSourceUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICMethod;
import com.pnfsoftware.jeb.core.util.DecompilerHelper;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;
import com.pnfsoftware.jeb.util.format.Strings;

/**
 * Plugin to run {@link SimpleCEmulator} on JEB's decompiled C code.
 * 
 * @author Joan Calvet
 *
 */
public class CEmulatorPlugin extends AbstractEnginesPlugin {
    private static final ILogger logger = GlobalLog.getLogger(CEmulatorPlugin.class);

    private Long firstRtnAddress = null;
    // optional memory dumps
    private MemoryDump stackDump;
    private MemoryDump heapDump;

    private File logFile;
    private boolean tracerMode;
    private boolean marsAnalyticaMode;

    public CEmulatorPlugin() {
    }

    public CEmulatorPlugin(long firstRtnAddress) {
        this.firstRtnAddress = firstRtnAddress;
    }

    public CEmulatorPlugin(long firstRtnAddress, MemoryDump stackDump, MemoryDump heapDump) {
        this(firstRtnAddress);
        this.stackDump = stackDump;
        this.heapDump = heapDump;
    }

    @Override
    public void load(IEnginesContext context) {
        logger.info("Loading C emulator plugin");
    }

    public void setLogFile(File logFile) {
        this.logFile = logFile;
    }

    private void parseParameters(Map<String, String> params) {
        if(params == null || params.isEmpty()) {
            return;
        }
        firstRtnAddress = Long.decode(params.get("FirstRtnAddr"));
        String logFilePath = params.get("LogFilePath");
        if(!logFilePath.isEmpty()) {
            logFile = new File(logFilePath);
        }
        tracerMode = Boolean.parseBoolean(params.get("TracerMode"));
        marsAnalyticaMode = Boolean.parseBoolean(params.get("MarsAnalyticaMode"));

    }

    @Override
    public void execute(IEnginesContext context, Map<String, String> params) {
        logger.info("Executing C emulator plugin");

        IRuntimeProject prj = context.getProject(0);
        ILiveArtifact a = prj.getLiveArtifact(0);
        IUnit topLevelUnit = a.getMainUnit();

        INativeCodeUnit<?> codeUnit = (INativeCodeUnit<?>)UnitUtil
                .findDescendantsByType(topLevelUnit, INativeCodeUnit.class, false).get(0);
        logger.info("Code unit: %s", codeUnit);

        if(!codeUnit.process()) {
            logger.info("ERROR: Failed to process code unit");
            return;
        }

        INativeDecompilerUnit<?> decomp = (INativeDecompilerUnit<?>)DecompilerHelper.getDecompiler(codeUnit);
        logger.info("Decompiler: %s", decomp);
        
        parseParameters(params);
        if(firstRtnAddress == null) {
            throw new JebRuntimeException("ERROR: address of routine to emulate is undefined");
        }

        // initial emulator state
        EmulatorState emulatorState;
        if(stackDump != null && heapDump != null) {
            emulatorState = new EmulatorState(codeUnit, stackDump, heapDump);
            emulatorState.setRegisterValue(SimpleCEmulator.REG_RBP_ID, stackDump.basePointer);
        }
        else {
            emulatorState = new EmulatorState(codeUnit);
            emulatorState.setRegisterValue(SimpleCEmulator.REG_RBP_ID, 0x7fffffffdf90L); //dummy value
        }
        emulatorState.allocateStackSpace();

        SimpleCEmulator emulator = marsAnalyticaMode ? new MarsAnalyticaCEmulator(): new SimpleCEmulator();

        // analyze first handler
        Long handlerAddress = firstRtnAddress;
        ICMethod handlerMethod = disassembleAndDecompile(decomp, handlerAddress);

        // tracing loop
        while(true) {
            logger.info("> emulating method %s...", handlerMethod.getName());

            // emulate handler
            EmulatorLog log = emulator.emulate(handlerMethod, emulatorState);
            emulatorState = log.getCurrentEmulatorState();

            // get next handler address
            handlerAddress = emulatorState.getRegisterValue(SimpleCEmulator.REG_NEXT_METHOD_ID);
            if(handlerAddress == null) {
                logger.info("  >> STOP: no next entry-point address found");
                break;
            }

            emulator.dumpLog(logFile);

            if(!tracerMode) {
                break;
            }

            logger.info("  >> done; found next method entry point to emulate: 0x%08x", handlerAddress);
            handlerMethod = disassembleAndDecompile(decomp, handlerAddress);
        }
    }

    /**
     * Disassemble and decompile (or use the existing decompiled unit).
     * 
     * @param decomp
     * @param methodAddress
     * @return decompiled method
     */
    private ICMethod disassembleAndDecompile(INativeDecompilerUnit<?> decomp, long methodAddress) {
        // disassemble, if needed
        if(!decomp.getCodeUnit().getCodeModel().isRoutineHeader(methodAddress)) {
            EntryPointDescription nextHandlerEPD = decomp.getCodeUnit().getProcessor().createEntryPoint(methodAddress);
            decomp.getCodeUnit().getCodeAnalyzer().enqueuePointerForAnalysis(nextHandlerEPD);
            decomp.getCodeUnit().getCodeAnalyzer().analyze();
        }

        // decompile, if needed
        String decompUnitId = Strings.ff("%x", methodAddress);
        return (ICMethod)((INativeSourceUnit)decomp.decompile(decompUnitId)).getASTItem();
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("CEmulator", "Plugin to emulate JEB's decompiled C code",
                "Joan Calvet (PNF Software)", Version.create(1, 0, 0));
    }

    @Override
    public List<? extends IOptionDefinition> getExecutionOptionDefinitions() {
        return Arrays.asList(new OptionDefinition("FirstRtnAddr", "Address of routine to emulate"),
                new BooleanOptionDefinition(
                        "TracerMode", true,
                        "Tracer mode enabled (emulator follows subroutine calls -- until it cannot anymore)"),
                new BooleanOptionDefinition(
                "MarsAnalyticaMode", true,
                        "MarsAnalytica's specific logic enabled"),
                new OptionDefinition("LogFilePath",
                        "Path to log file (optional -- if unspecified logs will be written as a sub unit in JEB project)"));
    }
}
