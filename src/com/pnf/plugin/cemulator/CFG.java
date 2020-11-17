package com.pnf.plugin.cemulator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICBlock;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICBreak;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICCompound;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICContinue;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICControlBreaker;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICForLoopStm;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICGoto;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICIfStm;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICJumpFar;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICMethod;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICReturn;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICStatement;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICWhileLoopStm;
import com.pnfsoftware.jeb.util.format.Strings;

/**
 * Control-Flow Graph (CFG) representing the control-flow of an {@link ICMethod}.
 * <p>
 * This class is a <b>minimal</b> implementation to be used by {@link SimpleCEmulator}. The computed
 * graph only provides the reachable statements from each statement (through
 * {@link #getNextStatement(ICStatement)} and {@link #getNthNextStatement(ICStatement, int)}).
 * <p>
 * Known limitations/simplifications:
 * <ul>
 * <li>statements are assumed to be unique within the method, and serves to identify graph nodes
 * (i.e. no basic blocks)
 * <li>for compound statements (see {@link ICCompound}), the embedded {@link ICBlock} are kept in
 * the CFG and transfer control to their first statement
 * <li>do-while loops are not specifically handled; they are ordered as while-loops
 * <li>switch-case are not handled
 * <li>predicates are not stored on their corresponding edges; it's the client responsibility to
 * retrieve them
 * <li>graph exit(s) are represented by 'null' nodes
 * </ul>
 * 
 * @author Joan Calvet
 *
 */
public class CFG {

    /**
     * Reachable statements (from -> to)
     * <p>
     * Implementation note: if there are several reachable statements, the first statement is the
     * fallthrough (see {@link #getNextStatement(ICStatement)}). Others statements correspond to
     * conditional branches (see {@link #getNthNextStatement(ICStatement, int)}.
     */
    private Map<ICStatement, List<ICStatement>> outEdges = new IdentityHashMap<>();

    /**
     * CFG's entry point
     */
    private ICStatement entryPoint;

    /**
     * Process method's AST and build the corresponding CFG.
     * 
     * @param method
     * @return cfg of the method
     */
    public static CFG buildCFG(ICMethod method) {
        CFG cfg = new CFG();
        buildCFGRecursive(cfg, method.getBody(), null, null);
        cfg.entryPoint = method.getBody().isEmpty() ? null: method.getBody().get(0);
        return cfg;
    }

    /**
     * Build a CFG by recursively processing {@link ICBlock}.
     * 
     * @param cfg
     * @param currentBlock the block to process
     * @param parentStatement the statement containing the block in the AST, null if none
     * @param parentLoop the loop containing the block in the AST, null if none
     */
    private static void buildCFGRecursive(CFG cfg, ICBlock currentBlock, ICStatement parentStatement,
            ICStatement parentLoop) {
        int index = 0;
        while(index < currentBlock.size()) {
            ICStatement curStatement = currentBlock.get(index);
            ICStatement parentLoop_ = parentLoop;

            // add default fallthrough edge on all statements
            // (only if not already set)
            ICStatement ftStatement = null;
            if(index < currentBlock.size() - 1) {
                ftStatement = currentBlock.get(index + 1);
            }
            else {
                // last element of the block: fallthrough is parent's fallthrough
                ftStatement = cfg.getNextStatement(parentStatement);
            }
            cfg.setFallThrough(curStatement, ftStatement, false);

            if(curStatement instanceof ICGoto) {
                cfg.setFallThrough(curStatement, ((ICGoto)curStatement).getLabel());
            }
            else if(curStatement instanceof ICWhileLoopStm || curStatement instanceof ICForLoopStm) {
                parentLoop_ = curStatement;
                ICBlock loopBody = ((ICCompound)curStatement).getBlocks().get(0);
                cfg.addConditionalTarget(curStatement, loopBody);

                // set backward edge
                if(loopBody.isEmpty()) {
                    cfg.setFallThrough(loopBody, curStatement);
                }
                else {
                    cfg.setFallThrough(loopBody.getLast(), curStatement);
                }
            }
            else if(curStatement instanceof ICIfStm) {
                // for each branch, add an edge
                List<? extends ICBlock> ifBlocks = ((ICIfStm)curStatement).getBlocks();
                for(int i = 0; i < ((ICIfStm)curStatement).size(); i++) {
                    cfg.addConditionalTarget(curStatement, ifBlocks.get(i));
                }
            }
            else if(curStatement instanceof ICReturn || curStatement instanceof ICJumpFar) {
                // end of method
                cfg.setFallThrough(curStatement, null);
            }
            else if(curStatement instanceof ICControlBreaker) {
                if(((ICControlBreaker)curStatement).getLabel() != null) {
                    cfg.setFallThrough(curStatement, ((ICControlBreaker)curStatement).getLabel());
                }
                else {
                    if(curStatement instanceof ICBreak) {
                        // goto parent loop's fallthrough
                        cfg.setFallThrough(curStatement, cfg.getNextStatement(parentLoop_));
                    }
                    else if(curStatement instanceof ICContinue) {
                        // goto parent loop
                        cfg.setFallThrough(curStatement, parentLoop_);
                    }
                }
            }
            else if(curStatement instanceof ICBlock) {
                cfg.setFallThrough(curStatement, ((ICBlock)curStatement).get(0));
            }

            // recursion
            if(curStatement instanceof ICCompound) {
                for(ICBlock subBlock: ((ICCompound)curStatement).getBlocks()) {
                    if(!subBlock.isEmpty()) {
                        cfg.setFallThrough(subBlock, subBlock.get(0));
                    }
                    buildCFGRecursive(cfg, subBlock, curStatement, parentLoop_);
                }
            }

            index += 1;
        }
    }


    /**
     * Get the statement reachable from the given statement, when its predicate is true. For
     * multi-predicates statement, see {@link #getNthNextStatement(ICStatement, int)}.
     * 
     * @param from
     * @return statement conditionally reachable, null if none
     */
    public ICStatement getNextTrueStatement(ICStatement from) {
        return outEdges.get(from) != null && outEdges.get(from).size() > 1 ? outEdges.get(from).get(1)
                : null;
    }

    /**
     * Get the statement reachable from the given statement, when its n-th predicate is true. This
     * method is the preferred one for multi-predicate statements (if-elseif-..), and the reachable
     * statements are ordered as they are in their container statement.
     * 
     * @param from
     * @param n
     * @return the n-th conditionally reachable statement, null if none
     */
    public ICStatement getNthNextStatement(ICStatement from, int n) {
        return outEdges.get(from) != null && outEdges.get(from).size() > n + 1
                ? outEdges.get(from).get(n + 1): null;
    }

    /**
     * Get next statement reachable from the given statement, defined as:
     * <p>
     * <ul>
     * <li>for unconditional statements: the next reachable statement (eg, target of goto statement
     * or next statement after an assignment)
     * <li>for conditional statements: the statement reachable when predicates are false
     * </ul>
     * 
     * @param from
     * @return first next possible statement, might be null
     */
    public ICStatement getNextStatement(ICStatement from) {
        return outEdges.get(from) != null ? outEdges.get(from).get(0): null;
    }

    private boolean setFallThrough(ICStatement from, ICStatement to) {
        return setFallThrough(from, to, true);
    }

    private boolean setFallThrough(ICStatement from, ICStatement to, boolean eraseExisting) {
        List<ICStatement> nexts = outEdges.get(from);
        if(nexts == null) {
            nexts = new ArrayList<>();
            outEdges.put(from, nexts);
        }
        if(eraseExisting && nexts.size() >= 1) {
            nexts.set(0, to);
            return true;
        }
        else if(nexts.isEmpty()) {
            nexts.add(to);
            return true;
        }
        return false;
    }

    /**
     * Add a new conditional reachable statement.
     * <p>
     * Important: this method assumes the fallthrough statement has already been set.
     * 
     * @param from
     * @param to
     * @return
     */
    private boolean addConditionalTarget(ICStatement from, ICStatement to) {
        List<ICStatement> nexts = outEdges.get(from);
        if(nexts == null) {
            nexts = new ArrayList<>();
            outEdges.put(from, nexts);
        }
        nexts.add(to);
        return true;
    }

    /**
     * Get CFG entry point.
     * 
     * @return cfg entry point, might be null
     */
    public ICStatement getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(ICStatement entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(ICStatement from: outEdges.keySet()) {
            sb.append("--edge--");
            sb.append(Strings.LINESEP);
            sb.append("> from:");
            sb.append(from.getClass().getSimpleName().toString());
            sb.append(Strings.LINESEP);
            sb.append(from);
            sb.append(Strings.LINESEP);
            for(ICStatement to: outEdges.get(from)) {
                if(to == null) {
                    sb.append("> to:EXIT");
                    sb.append(Strings.LINESEP);
                }
                else {
                    sb.append("> to:");
                    sb.append(to.getClass().getSimpleName().toString());
                    sb.append(Strings.LINESEP);
                    sb.append(to);
                    sb.append(Strings.LINESEP);
                }
            }
        }
        return sb.toString();
    }
}
