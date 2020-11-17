package com.pnf.plugin.cemulator;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.pnfsoftware.jeb.core.exceptions.JebRuntimeException;
import com.pnfsoftware.jeb.core.input.BytesInput;
import com.pnfsoftware.jeb.core.units.AbstractBinaryUnit;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.IUnit;
import com.pnfsoftware.jeb.core.units.WellKnownUnitTypes;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.COperatorType;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICAssignment;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICBlock;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICCall;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICConstantInteger;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICConstantPointer;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICControlBreaker;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICDecl;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICElement;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICExpression;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICGoto;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICIdentifier;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICIfStm;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICJumpFar;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICLabel;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICMethod;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICOperation;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICOperator;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICPredicate;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICReturn;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICStatement;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICWhileStm;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;
import com.pnfsoftware.jeb.util.math.MathUtil;
import com.pnfsoftware.jeb.util.format.Strings;
import com.pnfsoftware.jeb.util.io.IO;

/**
 * Simple emulator for {@link ICMethod} (JEB's AST). Originally implemented to be used with
 * {@link CEmulatorPlugin}.
 * <p>
 * Limitations:
 * <ul>
 * <li>Emulator relies on a minimalist CFG implementation (see {@link CFG}), and hence has the same
 * limitations (in particular switch and do-while statements are not emulated)
 * <li>Called subroutines are either simulated (ie, reimplemented in Java) or emulated as
 * non-retourning routines (see {{@link #evaluateCall(ICCall)})
 * <li>Emulator is tailored for x64 machine code, as it assumes calls' returned values are passed
 * through RAX register
 * </ul>
 * 
 * @author Joan Calvet
 *
 */
public class SimpleCEmulator {
    protected static final ILogger logger = GlobalLog.getLogger(SimpleCEmulator.class);

    /** IDs for register variables (mirrors) */
    public static final int REG_RAX_ID = -65536;
    public static final int REG_RBP_ID = -65856;
    public static final int REG_RBX_ID = -65728;
    /** synthetic register to store next method to emulate */
    public static final int REG_NEXT_METHOD_ID = 1;

    protected ICMethod method;
    protected EmulatorState state;

    protected boolean defaultLogging = true;
    protected StringBuilder outputLog = new StringBuilder();

    private IUnit logUnit;

    private int logSize = 0;

    /**
     * Emulate the given method using the given input state.
     * 
     * @param method
     * @param inputState
     * @return log of the emulation
     */
    public EmulatorLog emulate(ICMethod method, EmulatorState inputState) {
        preEmulateMethodCallback(method, inputState);

        state = inputState;
        this.method = method;

        initEmulation();
        EmulatorLog log = new EmulatorLog();

        CFG cfg = CFG.buildCFG(method);
        ICStatement currentStatement = cfg.getEntryPoint();

        if(defaultLogging) {
            outputLog.append("> emulator trace:");
            outputLog.append(Strings.LINESEP);
        }

        while(currentStatement != null) {
            log.addExecutedStatement(currentStatement);

            if(defaultLogging) {
                outputLog.append(Strings.ff(" %s", currentStatement));
                outputLog.append(Strings.LINESEP);
            }

            currentStatement = emulateStatement(cfg, currentStatement);

            // uncomment to see register + memory state
            //            if(defaultLogging) {
            //                outputLog.append(Strings.ff("  > output registers: %s", state.toString()));
            //                outputLog.append(Strings.LINESEP);
            //            }
        }

        log.setEmulatorState(state);
        return log;
    }

    protected void preEmulateMethodCallback(ICMethod method, EmulatorState inputState) {
        // default implementation does nothing - override with specific logic
        return;
    }

    protected void initEmulation() {
        // default implementation does nothing - override with specific logic
        return;
    }

    protected void preEmulateStatementCallback(CFG cfg, ICStatement currentStatement) {
        // default implementation does nothing - override with specific logic
        return;
    }

    private ICStatement emulateStatement(CFG cfg, ICStatement currentStatement) {
        preEmulateStatementCallback(cfg, currentStatement);

        if(currentStatement instanceof ICGoto) {
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICLabel) {
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICReturn) {
            ICExpression retExpression = ((ICReturn)currentStatement).getExpression();
            if(retExpression != null) {
                // note: would need to check calling convention
                state.setRegisterValue(REG_RAX_ID,
                        evaluateExpression(retExpression));
            }
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICAssignment) {
            evaluateAssignment((ICAssignment)currentStatement);
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICIfStm) {
            ICIfStm ifStm = (ICIfStm)currentStatement;
            List<? extends ICPredicate> predicates = ifStm.getBranchPredicates();
            for(int i = 0; i < predicates.size(); i++) {
                if(evaluateExpression(predicates.get(i)) != 0) {
                    return cfg.getNthNextStatement(currentStatement, i);
                }
            }
            // ...or go to else block if present (last conditional target)
            if(ifStm.hasDefaultBlock()) {
                return cfg.getNthNextStatement(currentStatement, predicates.size());
            }
            // ...or to fallthrough
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICWhileStm) {
            ICWhileStm wStm = (ICWhileStm)currentStatement;
            if(evaluateExpression(wStm.getPredicate()) != 0) {
                return cfg.getNextTrueStatement(wStm);
            }
            else {
                return cfg.getNextStatement(wStm);
            }
        }
        else if(currentStatement instanceof ICBlock) {
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICControlBreaker) {
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICDecl) {
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICCall) {
            evaluateCall((ICCall)currentStatement);
            return cfg.getNextStatement(currentStatement);
        }
        else if(currentStatement instanceof ICJumpFar) {
            long targetAddr = evaluateExpression(((ICJumpFar)currentStatement).getJumpsite());
            state.setRegisterValue(REG_NEXT_METHOD_ID, targetAddr);
            return cfg.getNextStatement(currentStatement);
        }
        else {
            throw new EmulatorException(
                    Strings.ff("ERROR: unimplemented statement emulation (%s)", currentStatement));
        }
    }

    private void evaluateCall(ICCall ccall) {
        if(ccall.getMethod() != null) { // resolved calls
            Long returnValue = simulateWellKnownMethods(ccall.getMethod(),
                    ccall.getArguments());
            if(returnValue == null) {
                // simulation failed, we need to emulate callee
                throw new EmulatorException(
                        Strings.ff("ERROR: unimplemented recursive emulation (%s)", ccall));
            }
            else {
                state.setRegisterValue(REG_RAX_ID, returnValue);
            }
        }
        else { // calls whose target is not resolved yet
              // here we need to go emulate target, and we assume such call is non-returning
            Long nextHandlerAddr = evaluateExpression(ccall.getCallsite());
            if(nextHandlerAddr == null) {
                throw new EmulatorException(Strings.ff("ERROR: cannot resolve target (%s)", ccall));
            }
            else {
                state.setRegisterValue(REG_NEXT_METHOD_ID, nextHandlerAddr);
            }
        }
    }

    private void evaluateAssignment(ICAssignment assign) {
        if(assign.isSimpleAssignment()) {
            // right hand side eval
            Long rightValue = evaluateExpression(assign.getRight());
            if(rightValue == null){
                throw new EmulatorException(Strings.ff("right value evaluation (%s)", assign));
            }
            // left hand side eval
            ICExpression leftDerefExpr = getDereferencedExpression(assign.getLeft());
            if(leftDerefExpr != null) {
                Long leftExprValue = evaluateExpression(leftDerefExpr);
                if(leftExprValue != null) {
                    // memory access
                    if(((ICOperation)assign.getLeft()).getFirstOperand() instanceof ICOperation) {
                        ICOperation leftFirstOperand = (ICOperation)(((ICOperation)assign.getLeft()).getFirstOperand());
                        if(leftFirstOperand.getOperator().isCast()) {
                            int derefCastSize = state
                                    .getBaseTypeSize(((ICOperation)leftFirstOperand).getOperator().getCastType());
                            state.writeMemory(leftExprValue, rightValue, derefCastSize);
                        }
                        else {
                            state.writeMemory(leftExprValue, rightValue, 8);
                        }
                    }
                }
            }
            else {
                // identifier (possibly within a definition)
                state.setVarValue(assign.getLeft(), rightValue);
            }
        }
        else {
            throw new EmulatorException("ERROR: not implemented: non simple assignments");
        }
    }

    private ICExpression getDereferencedExpression(ICElement element) {
        if(element instanceof ICOperation) {
            if(((ICOperation)element).getOperatorType() == COperatorType.PTR) {
                return ((ICOperation)element).getFirstOperand();
            }
        }
        return null;
    }

    protected Long evaluateExpression(ICExpression expr) {
        if(expr instanceof ICConstantInteger) {
            return ((ICConstantInteger<?>)expr).getValueAsLong();
        }
        else if(expr instanceof ICConstantPointer) {
            return ((ICConstantPointer)expr).getValue();
        }
        else if(expr instanceof ICOperation) {
            return evaluateOperation((ICOperation)expr);
        }
        else if(expr instanceof ICIdentifier) {
            Long varValue = state.getVarValue((ICIdentifier)expr);
            if(varValue == null) {
                logger.info("> warning: non initialized identifier (%s) -- defining it to 0L", expr);
                varValue = 0L;
            }
            return varValue;
        }
        else if(expr instanceof ICPredicate) {
            return evaluateExpression(((ICPredicate)expr).getExpression()) != 0 ? 1L: 0L;
        }
        else if(expr instanceof ICCall) {
            evaluateCall(((ICCall)expr));
            return state.getRegisterValue(REG_RAX_ID);
        }
        else {
            throw new EmulatorException(Strings.ff("ERROR: unimplemented expression eval (%s)", expr));
        }
    }

    /**
     * Simulate well known methods; ie, rather than emulating their actual code, we simulate it in
     * Java.
     * 
     * @return method return value, null if failure
     */
    protected Long simulateWellKnownMethods(ICMethod calledMethod,
            List<ICExpression> parameters) {
        /**
         * libc APIs
         */
        if(calledMethod.getName().equals("→time")) {
            return 42L;
        }
        else if(calledMethod.getName().equals("→srand")) {
            return 37L;
        }
        else if(calledMethod.getName().equals("→memcpy")) {
            ICExpression dst = parameters.get(0);
            ICExpression src = parameters.get(1);
            ICExpression n = parameters.get(2);
            if(src instanceof ICOperation && ((ICOperation)src).checkOperatorType(COperatorType.CAST)
                    && n instanceof ICConstantInteger) {
                long src_ = ((ICConstantPointer)((ICOperation)src).getFirstOperand()).getValue();
                int n_ = (int)((ICConstantInteger<?>)n).getValueAsLong();
                long dst_ = evaluateExpression(dst);
                state.copyMemory(src_, dst_, n_);
                return dst_;
            }
            else {
                throw new EmulatorException("TBI: memcpy");
            }
        }


        return null;
    }

    private Long evaluateOperation(ICOperation operation) {
        Long value = null;

        ICExpression opnd1 = operation.getFirstOperand();
        ICExpression opnd2 = operation.getSecondOperand();
        ICExpression opnd3 = operation.getThirdOperand();
        ICOperator operator = operation.getOperator();

        switch(operator.getType()) {
        case ADD:
            value = evaluateExpression(opnd1) + evaluateExpression(opnd2);
            break;
        case AND:
            value = evaluateExpression(opnd1) & evaluateExpression(opnd2);
            break;
        case CAST:
            int castSize = state.getTypeSize(operator.getCastType());
            long castOperand = MathUtil.makeMask(castSize * 8);
            value = evaluateExpression(opnd1) & castOperand;
            break;
        case COND:
            value = evaluateExpression(opnd1) != 0 ? evaluateExpression(opnd2): evaluateExpression(opnd3);
            break;
        case CUSTOM:
            break;
        case DIV:
            value = evaluateExpression(opnd1) / evaluateExpression(opnd2);
            break;
        case EQ:
            value = evaluateExpression(opnd1).equals(evaluateExpression(opnd2)) ? 1L: 0L;
            break;
        case GE:
            value = evaluateExpression(opnd1) >= evaluateExpression(opnd2) ? 1L: 0L;
            break;
        case GT:
            value = evaluateExpression(opnd1) > evaluateExpression(opnd2) ? 1L: 0L;
            break;
        case LE:
            value = evaluateExpression(opnd1) <= evaluateExpression(opnd2) ? 1L: 0L;
            break;
        case LOG_AND:
            value = (evaluateExpression(opnd1) != 0 && evaluateExpression(opnd2) != 0) ? 1L: 0L;
            break;
        case LOG_IDENT:
            value = evaluateExpression(opnd1);
            break;
        case LOG_NOT:
            value = evaluateExpression(opnd1) != 0 ? 0L: 1L;
            break;
        case LOG_OR:
            value = (evaluateExpression(opnd1) != 0 || evaluateExpression(opnd2) != 0) ? 1L: 0L;
            break;
        case LT:
            value = evaluateExpression(opnd1) < evaluateExpression(opnd2) ? 1L: 0L;
            break;
        case MUL:
            value = evaluateExpression(opnd1) * evaluateExpression(opnd2);
            break;
        case NE:
            value = evaluateExpression(opnd1) != evaluateExpression(opnd2) ? 1L: 0L;
            break;
        case NEG:
            value = -evaluateExpression(opnd1);
            break;
        case NOT:
            value = ~evaluateExpression(opnd1);
            break;
        case OR:
            value = evaluateExpression(opnd1) | evaluateExpression(opnd2);
            break;
        case PTR:
            if(opnd1 instanceof ICIdentifier) {
                value = state.readMemorySafe(evaluateExpression(opnd1),
                        state.getBaseTypeSize(((ICIdentifier)opnd1).getType()));
            }
            else if(opnd1 instanceof ICOperation) {
                ICIdentifier basePointer = getBasePointer((ICOperation)opnd1);
                if(((ICOperation)opnd1).getOperator().isCast()) {
                    int derefCastSize = state
                            .getBaseTypeSize(((ICOperation)opnd1).getOperator().getCastType());
                    value = state.readMemorySafe(
                            evaluateExpression(((ICExpression)((ICOperation)opnd1).getFirstOperand())),
                            derefCastSize);
                    value = MathUtil.signExtend(value, derefCastSize * 8);
                }
                else if(basePointer != null) {
                    value = state.readMemorySafe(evaluateExpression(opnd1),
                            state.getBaseTypeSize(basePointer.getType()));
                }
                else {
                    if(state.getDefaultPointerSize() != null) {
                        value = state.readMemorySafe(evaluateExpression(opnd1),
                                state.getDefaultPointerSize());
                    }
                    else {
                        throw new EmulatorException("cant find size to read for PTR operation");
                    }
                }
            }
            else if(opnd1 instanceof ICConstantInteger) {
                logger.info("> warning: read with fixed size (%d) at address %x",
                        state.getDefaultPointerSize(),
                        ((ICConstantInteger<?>)opnd1).getValueAsLong());
                value = state.readMemorySafe(((ICConstantInteger<?>)opnd1).getValueAsLong(),
                        state.getDefaultPointerSize());
            }
            else {
                throw new EmulatorException(Strings.ff("PTR invalid (%s)", opnd1));
            }
            break;
        case REF:
            if(!(opnd1 instanceof ICIdentifier)) {
                throw new EmulatorException(Strings.ff("REF on non id (%s)", opnd1));
            }
            value = state.getVarAddress((ICIdentifier)opnd1);
            break;
        case REM:
            value = evaluateExpression(opnd1) % evaluateExpression(opnd2);
            break;
        case SHL:
            value = evaluateExpression(opnd1) << evaluateExpression(opnd2);
            break;
        case SHR:
            value = evaluateExpression(opnd1) >> evaluateExpression(opnd2);
            break;
        case SIZEOF:
            break;
        case SUB:
            value = evaluateExpression(opnd1) - evaluateExpression(opnd2);
            break;
        case USHR:
            value = evaluateExpression(opnd1) >>> evaluateExpression(opnd2);
            break;
        case XOR:
            value = evaluateExpression(opnd1) ^ evaluateExpression(opnd2);
            break;
        default:
            break;
        }
        if(value == null) {
            throw new EmulatorException(Strings.ff("TBI: operator (%s)", operator));
        }
        return value;
    }

    /**
     * Get the base pointer in a pointer arithmetic operation. Simple check for the first identifier
     * in the operation.
     * 
     * @param operation
     * @return base pointer, null if cannot be found
     */
    public ICIdentifier getBasePointer(ICOperation operation) {
        ICIdentifier basePointer = null;
        if(operation.getFirstOperand() instanceof ICIdentifier) {
            basePointer = (ICIdentifier)operation.getFirstOperand();
        }
        else if(operation.getSecondOperand() instanceof ICIdentifier) {
            basePointer = (ICIdentifier)operation.getSecondOperand();
        }
        else if(operation.getThirdOperand() instanceof ICIdentifier) {
            basePointer = (ICIdentifier)operation.getThirdOperand();
        }
        return basePointer;
    }

    public void dumpLog(File logFile) {
        if(logFile != null) {
            if(outputLog.length() != logSize) { // if something new...
                try {
                    IO.writeFile(logFile, Strings.encodeUTF8(outputLog.toString()));
                    logSize = outputLog.length();
                }
                catch(IOException e) {
                    throw new JebRuntimeException("failed to write log file");
                }
            }
        }
        else {
            // dump as text unit
            INativeCodeUnit<?> codeUnit = state.getNativeCodeUnit();
            if(codeUnit != null && codeUnit.getCodeObjectContainer() != null) {
                if(logUnit == null) {
                    logUnit = codeUnit.getUnitProcessor().process("C emulator log",
                            new BytesInput(Strings.encodeUTF8(outputLog.toString())), codeUnit.getCodeObjectContainer(),
                            WellKnownUnitTypes.typeGeneric);
                    codeUnit.getCodeObjectContainer().addChild(logUnit);
                }
                else {
                    ((AbstractBinaryUnit)logUnit).setInput(new BytesInput(Strings.encodeUTF8(outputLog.toString())));
                }
            }
        }
    }

}
