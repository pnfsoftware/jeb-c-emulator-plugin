package com.pnf.plugin.cemulator;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.pnfsoftware.jeb.core.exceptions.JebRuntimeException;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.CIdentifierClass;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICDecl;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICElement;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICIdentifier;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICType;
import com.pnfsoftware.jeb.core.units.code.asm.memory.IVirtualMemory;
import com.pnfsoftware.jeb.core.units.code.asm.memory.MemoryException;
import com.pnfsoftware.jeb.core.units.code.asm.memory.VirtualMemoryUtil;
import com.pnfsoftware.jeb.core.units.code.asm.type.INativeType;
import com.pnfsoftware.jeb.core.units.code.asm.type.ITypeManager;
import com.pnfsoftware.jeb.util.base.Assert;
import com.pnfsoftware.jeb.util.format.Strings;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;

/**
 * State of the emulator (memory + registers)
 * 
 * @author Joan Calvet
 *
 */
public class EmulatorState {
    private static final ILogger logger = GlobalLog.getLogger(EmulatorState.class);

    private IVirtualMemory memory;
    private Map<Integer, Long> registers = new HashMap<>(); // id -> value

    /** default pointer size, in bytes */
    private Integer defaultPointerSize;
    private INativeCodeUnit<?> nativeUnit;
    private ITypeManager typeManager;

    static class MemoryDump {
        long baseAddress;
        File dumpFile;
        long basePointer; // optional

        public MemoryDump(long baseAddress, File dumpFile) {
            this.baseAddress = baseAddress;
            this.dumpFile = dumpFile;
        }

        public MemoryDump(long baseAddress, File dumpFile, long basePointer) {
            this(baseAddress, dumpFile);
            this.basePointer = basePointer;
        }
    }

    public EmulatorState(INativeCodeUnit<?> nativeUnit) {
        Assert.a(nativeUnit != null);
        this.nativeUnit = nativeUnit;
        typeManager = nativeUnit.getTypeManager();

        // memory initialization
        defaultPointerSize = nativeUnit.getMemory().getSpaceBits() / 8;
        memory = nativeUnit.getMemory();
    }

    /**
     * Initialize state from stack/heap memory dumps
     */
    public EmulatorState(INativeCodeUnit<?> nativeUnit, MemoryDump stackDump, MemoryDump heapDump) {
        Assert.a(nativeUnit != null);
        this.typeManager = nativeUnit.getTypeManager();
        defaultPointerSize = nativeUnit.getMemory().getSpaceBits() / 8;
        this.memory = nativeUnit.getMemory().duplicate();

        // stack allocation
        byte[] src = null;
        try {
            src = Files.readAllBytes(stackDump.dumpFile.toPath());
            allocateMemory(stackDump.baseAddress, src.length);
            this.memory.write(stackDump.baseAddress, src.length, src, 0);
        }
        catch(IOException e) {
            throw new JebRuntimeException("error when reading stack dump");
        }

        // heap allocation
        src = null;
        try {
            src = Files.readAllBytes(heapDump.dumpFile.toPath());
            allocateMemory(heapDump.baseAddress, src.length);
            this.memory.write(heapDump.baseAddress, src.length, src, 0);
        }
        catch(IOException e) {
            throw new JebRuntimeException("error when reading heap dump");
        }
    }

    public void allocateMemory(long baseAddress, int size) {
        VirtualMemoryUtil.allocateFillGaps(this.memory, baseAddress, size, IVirtualMemory.ACCESS_RW);
    }

    public boolean allocateStackSpace() {
        Long baseStackPointerValue = getRegisterValue(SimpleCEmulator.REG_RBP_ID);
        if(baseStackPointerValue != null) {
            // arbitrary size
            VirtualMemoryUtil.allocateFillGaps(memory, (baseStackPointerValue & 0xFFFFFFFFFFFFF000L) - 0x10_0000,
                    0x11_0000, IVirtualMemory.ACCESS_RW);
            return true;
        }
        return false;
    }

    public Long getVarValue(ICElement element) {
        ICIdentifier id = getIdentifier(element);
        if(id.getIdentifierClass() == CIdentifierClass.LOCAL || id.getIdentifierClass() == CIdentifierClass.GLOBAL) {
            return readMemory(getVarAddress(id), getTypeSize(id.getType()));
        }
        else {
            return registers.get(id.getId());
        }
    }

    public void setVarValue(ICElement element, long value) {
        ICIdentifier id = getIdentifier(element);
        if(id.getIdentifierClass() == CIdentifierClass.LOCAL || id.getIdentifierClass() == CIdentifierClass.GLOBAL) {
            writeMemory(getVarAddress(id), value, getTypeSize(id.getType()));
        }
        else {
            int typeSize = getTypeSize(id.getType());
            switch(typeSize) {
            case 8:
                registers.put(id.getId(), value);
                break;
            case 4:
                registers.put(id.getId(), value & 0xFFFFFFFFL);
                break;
            case 2:
                registers.put(id.getId(), value & 0xFFFFL);
                break;
            case 1:
                registers.put(id.getId(), value & 0xFFL);
                break;
            default:
                throw new EmulatorException(Strings.ff("TBI: register size %d", typeSize));
            }
        }
    }

    private ICIdentifier getIdentifier(ICElement element) {
        if(element instanceof ICIdentifier) {
            return (ICIdentifier)element;
        }
        if(element instanceof ICDecl) {
            return ((ICDecl)element).getIdentifier();
        }
        return null;
    }

    public Long getVarAddress(ICIdentifier var) {
        if(var.getIdentifierClass() == CIdentifierClass.LOCAL) {
            return var.getAddress() + getRegisterValue(SimpleCEmulator.REG_RBP_ID) + 8; // we assume stack does not change
        }
        else if(var.getIdentifierClass() == CIdentifierClass.GLOBAL) {
            return var.getAddress();
        }
        else {
            throw new EmulatorException(Strings.ff("TBI: get address for var (%s)", var));
        }
    }

    /**
     * Get type size in bytes.
     * 
     * @param type
     * @return type size in bytes
     */
    public int getTypeSize(ICType type) {
        String typeName = type.getSignature();
        INativeType typeItem = typeManager.getType(typeName);
        if(typeItem == null) {
            throw new EmulatorException(Strings.ff("ERROR: unknown type (%s)", typeName));
        }
        return typeItem.getSize();
    }

    /**
     * Get base type size in bytes, i.e. the size of TYPE in 'TYPE *'
     */
    public int getBaseTypeSize(ICType type) {
        String typeName = type.getSignature();
        if(typeName.endsWith("*")) {
            INativeType typeItem = typeManager.getType(type.getBaseTypeSignature());
            if(typeItem == null) {
                throw new EmulatorException(Strings.ff("unknown base type (%s)", typeName));
            }
            return typeItem.getSize();
        }
        else {
            if(defaultPointerSize != null) {
                return defaultPointerSize;
            }
            throw new EmulatorException(Strings.ff("not a pointer type (%s)", typeName));
        }
    }

    /**
     * Copy n bytes from source to destination
     * 
     * @param src source address
     * @param dst destination address
     * @param n number of bytes to copy
     */
    public void copyMemory(long src, long dst, int n) {
        byte[] toCopy = new byte[n];
        try {
            memory.read(src, n, toCopy, 0);
            memory.write(dst, n, toCopy, 0);
        }
        catch(MemoryException e) {
            throw new EmulatorException("ERROR: memory copy failed");
        }
    }

    /**
     * Read memory with default endianness. Default value (0L) is returned when memory read failed.
     * 
     * @param address
     * @param bytesToRead number of bytes to read
     * @return read value, upper-casted as long, if memory couldn't be read return 0
     */
    public Long readMemorySafe(long address, int bytesToRead) {
        try {
            return readMemory(address, bytesToRead);
        }
        catch(EmulatorException e) {
            logger.info("> warning: cannot read memory at 0x%08x -- returning 0L", address);
            return 0L;
        }
    }

    /**
     * Read memory with default endianness.
     * 
     * @param address
     * @param bytesToRead number of bytes to read
     * @return read value, upper-casted as long
     */
    public Long readMemory(long address, int bytesToRead) {
        Long value = 0L;
        try {
            switch(bytesToRead) {
            case 8:
                value = memory.readLong(address);
                break;
            case 4:
                value = memory.readInt(address) & 0xFFFFFFFFL;
                break;
            case 2:
                value = memory.readShort(address) & 0xFFFFL;
                break;
            case 1:
                value = memory.readByte(address) & 0xFFL;
                break;
            default:
                throw new EmulatorException(Strings.ff("TBI: read memory size (%d)", bytesToRead));
            }
        }
        catch(MemoryException e) {
            throw new EmulatorException("ERROR: cant read memory");
        }
        return value;
    }

    /**
     * Write memory with default endianness.
     * 
     * @param address
     * @param value
     * @param bytesToWrite
     */
    public void writeMemory(long address, long value, int bytesToWrite) {
        try {
            switch(bytesToWrite) {
            case 8:
                memory.writeLong(address, value);
                break;
            case 4:
                memory.writeInt(address, (int)value);
                break;
            case 2:
                memory.writeShort(address, (short)value);
                break;
            case 1:
                memory.writeByte(address, (byte)value);
                break;
            default:
                throw new EmulatorException(Strings.ff("TBI: write memory size (%d)", bytesToWrite));
            }
        }
        catch(MemoryException e) {
            throw new EmulatorException("ERROR: cant write memory");
        }
    }

    public void setRegisterValue(int id, long value) {
        registers.put(id, value);
    }

    /**
     * Get register value
     * 
     * @param id
     * @return register value, null if not set
     */
    public Long getRegisterValue(int id) {
        return registers.get(id);
    }

    public Integer getDefaultPointerSize() {
        return defaultPointerSize;
    }

    public void setDefaultPointerSize(Integer defaultPointedSize) {
        this.defaultPointerSize = defaultPointedSize;
    }

    public INativeCodeUnit<?> getNativeCodeUnit() {
        return nativeUnit;
    }

    public String toRegisterString() {
        return "registers=" + registers;
    }

    @Override
    public String toString() {
        return "[memory=" + memory + ", registers=" + registers + "]";
    }

}
