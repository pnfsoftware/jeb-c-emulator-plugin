package com.pnf.plugin.cemulator;

import java.util.ArrayList;
import java.util.List;

import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICStatement;

/**
 * Log of a method emulation done by {@link SimpleCEmulator}. Provides access to the execution trace
 * and the current emulator state.
 * 
 * @author Joan Calvet
 *
 */
public class EmulatorLog {

    private List<String> executionTrace = new ArrayList<>();
    private EmulatorState currentState;

    public void addExecutedStatement(ICStatement stmt) {
        executionTrace.add(stmt.toString());
    }

    /**
     * Get the list of string representations of the executed statements
     */
    public List<String> getExecutionTrace() {
        return executionTrace;
    }

    public void setEmulatorState(EmulatorState state) {
        currentState = state;
    }

    public EmulatorState getCurrentEmulatorState() {
        return currentState;
    }

}
