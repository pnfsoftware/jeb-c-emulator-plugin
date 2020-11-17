package com.pnf.plugin.cemulator;

import com.pnfsoftware.jeb.core.exceptions.JebRuntimeException;

/**
 * Exception for {@link SimpleCEmulator}
 * 
 * @author Joan Calvet
 *
 */
public class EmulatorException extends JebRuntimeException {
    private static final long serialVersionUID = 1L;

    public EmulatorException() {
        super();
    }

    public EmulatorException(String message) {
        super(message);
    }

    public EmulatorException(Throwable cause) {
        super(cause);
    }

    public EmulatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
