package com.pnf.plugin.cemulator;

import java.util.List;

import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICConstantInteger;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICExpression;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICIfStm;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICMethod;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICOperation;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICStatement;
import com.pnfsoftware.jeb.util.format.Strings;

/**
 * MarsAnalytica's crackme specific emulation logic
 * 
 * @author Joan Calvet
 *
 */
public class MarsAnalyticaCEmulator extends SimpleCEmulator {

    private char currentChar = 0x61;

    /** Stack machine memory layout */
    private final int CHUNK_SIZE = 16;
    private long curFreeChunkAddr = 0x1000000; // arbitrary address 

    private int popCounter;

    @Override
    protected void initEmulation() {
        super.initEmulation();
        popCounter = 0;
        defaultLogging = false; // MA emulator does its own logging for stack machine operations
    }

    @Override
    protected void preEmulateMethodCallback(ICMethod method, EmulatorState inputState) {
        if(method.getName().equals("sub_402AB2")) {
            outputLog.append(Strings.ff("S: SWAP"));
            outputLog.append(Strings.LINESEP);
        }
    }

    @Override
    protected void preEmulateStatementCallback(CFG cfg, ICStatement currentStatement) {
        if(popCounter > 0) {
            if(currentStatement instanceof ICIfStm) {
                ICIfStm ifStm = ((ICIfStm)currentStatement);
                // MarsAnalytica ifs are always if/else or if
                if(ifStm.getBlocks().size() <= 2) {

                    // we log the 'true' predicate
                    ICOperation pred = (ICOperation)((ICIfStm)currentStatement).getBranchPredicate(0).getExpression()
                            .duplicate();
                    if(evaluateExpression(pred) == 0) {
                        pred.reverse(method.getOperatorFactory());
                    }
                    if(pred.getSecondOperand() instanceof ICConstantInteger) {
                        outputLog.append(String.format("S: TEST (%s,cte=%d)", pred.getOperator(),
                                ((ICConstantInteger<?>)pred.getSecondOperand()).getValueAsLong()));
                        outputLog.append(Strings.LINESEP);
                    }
                    else {
                        outputLog.append(
                                Strings.ff("S: TEST (%s,#op=%d)", pred.getOperator(), pred.getCountOfOperands()));
                        outputLog.append(Strings.LINESEP);
                    }
                    popCounter = 0;
                }
            }
        }
        return;
    }

    @Override
    protected Long simulateWellKnownMethods(ICMethod calledMethod, List<ICExpression> parameters) {
        Long defaultEmulationResult = super.simulateWellKnownMethods(calledMethod, parameters);
        if(defaultEmulationResult != null) {
            return defaultEmulationResult;
        }

        /** MarsAnalytica's specific emulation */

        /** Inject dummy characters */
        if(calledMethod.getName().equals("→getchar")) {
            return (long)currentChar++;
        }
        if(calledMethod.getName().equals("→putchar")) {
            logger.i("putchar");
            return 0L;
        }

        /**
         * Stack machine handlers
         */

        /** PUSH(STACK_PTR, VALUE) */
        else if(calledMethod.getName().equals("sub_400AAE")) {
            Long pStackPtr = evaluateExpression(parameters.get(0));
            Long pValue = evaluateExpression(parameters.get(1));

            long newChunkAddr = allocateNewChunk();

            // write value
            state.writeMemory(newChunkAddr + 8, pValue, 4);

            // link new chunk to existing stack
            Long stackAdr = state.readMemory(pStackPtr, 8);
            state.writeMemory(newChunkAddr, stackAdr, 8);

            // make new chunk the new stack head
            state.writeMemory(pStackPtr, newChunkAddr, 8);

            outputLog.append(Strings.ff("S: PUSH %d", pValue));
            outputLog.append(Strings.LINESEP);

            if(popCounter == 2) {
                // parameters.get(1) is an operation?
                ICExpression expr = parameters.get(1);
                if(expr instanceof ICOperation) {
                    // cast + operation
                    while(expr instanceof ICOperation && ((ICOperation)expr).getOperator().isCast()) {
                        expr = ((ICOperation)expr).getFirstOperand();
                    }
                }
                if(expr instanceof ICOperation) {
                    outputLog.append(Strings.ff("  | operation: (%s,#op=%d)", ((ICOperation)expr).getOperator(),
                            ((ICOperation)expr).getCountOfOperands()));
                    outputLog.append(Strings.LINESEP);
                    popCounter = 0;
                }
            }

            return 0L;
        }
        /** SET(STACK_PTR, INDEX, VALUE) */
        else if(calledMethod.getName().equals("sub_400D55")) {
            Long pStackPtr = evaluateExpression(parameters.get(0));
            Long pIndex = evaluateExpression(parameters.get(1));
            Long pValue = evaluateExpression(parameters.get(2));
            Long retVal = emulateSetElementFromEnd(pStackPtr, pIndex, pValue);

            outputLog.append(Strings.ff("S: SET index:%d value:%d", pIndex, pValue));
            outputLog.append(Strings.LINESEP);

            return retVal;
        }
        /** GET(STACK_PTR, INDEX) */
        else if(calledMethod.getName().equals("sub_400D08")) {
            Long pStackPtr = evaluateExpression(parameters.get(0));
            Long pIndex = evaluateExpression(parameters.get(1));
            Long retVal = emulateGetElementFromEnd(pStackPtr, pIndex);

            outputLog.append(Strings.ff("S: GET index:%d", pIndex));
            outputLog.append(Strings.LINESEP);

            return retVal;
        }
        /** POP(STACK_PTR) */
        else if(calledMethod.getName().equals("sub_4009D7")) {
            Long pStackPtr = evaluateExpression(parameters.get(0));
            Long retVal = emulateUnlink(pStackPtr);

            outputLog.append(Strings.ff("S: POP (%d)", retVal));
            outputLog.append(Strings.LINESEP);

            popCounter++;

            return retVal;
        }
        else if(calledMethod.getName().equals("sub_402AB2")) {
            outputLog.append(Strings.ff("S: SWAP"));
            outputLog.append(Strings.LINESEP);

            return 0L;
        }

        return null;
    }


    private long allocateNewChunk() {
        long freeChunkAddr = curFreeChunkAddr;
        curFreeChunkAddr += CHUNK_SIZE;
        state.allocateMemory(freeChunkAddr, CHUNK_SIZE);
        return freeChunkAddr;
    }

    private Long emulateSetElementFromEnd(Long param1, Long param2, Long param3) {
        long lastIndex = emulateGetLength(param1) - 1;
        long curElement = param1;
        for(int i = 0; i != lastIndex - param2; i++) {
            curElement = state.readMemory(curElement, 8);
        }
        state.writeMemory(curElement + 8, param3, 4);
        return curElement;
    }

    private Long emulateGetElementFromEnd(Long param1, Long param2) {
        long lastIndex = emulateGetLength(param1) - 1;
        long curElement = param1;
        for(int i = 0; i != lastIndex - param2; i++) {
            curElement = state.readMemory(curElement, 8);
        }
        return state.readMemory(curElement + 8, 4);
    }

    private Long emulateGetLength(Long param1) {
        long length = 0;
        long current = param1;
        while(current != 0) {
            current = state.readMemory(current, 8);
            length++;
        }
        return length;
    }

    private Long emulateUnlink(Long param1) {
        Long nextChunkAddress = state.readMemory(param1, 8);
        Long nextChunkValue = state.readMemory(nextChunkAddress + 8, 4);
        Long nextNextChunkAddress = state.readMemory(nextChunkAddress, 8);
        state.writeMemory(param1, nextNextChunkAddress, 8);
        // note: we do not free memory
        return nextChunkValue;
    }



}
