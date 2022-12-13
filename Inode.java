public class Inode {
    public static final int iNodeSize = 32;
    public static final int directSize = 11;
    public static final int IN_USE = -1;
    public static final int EMPTY = 0;
    public static final int AVAILABLE = 1;
    public int length;
    public short count;
    public short flag;
    public short[] direct = new short[directSize];
    public short indirect;
    // constructor for inode
    public Inode() {
        length = 0;
        count = 0;
        flag = 0;
        for (int i = 0; i < directSize; i++) {
            direct[i] = -1;
        }
        indirect = -1;
    }
    // inode constructor with parameter overide
    public Inode(short num) {
        int blockNumber = 1 + num / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, data);
        int offset = (num % 16) * iNodeSize;
        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data, offset);
        offset += 2;
        for (int i = 0; i < directSize; i++) {
            direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
    }
    // read/write to disk
    public void toDisk(short num) {
        byte[] data = new byte[iNodeSize];
        int offset = 0;
        SysLib.int2bytes(length, data, offset);
        offset += 4;
        SysLib.short2bytes(count, data, offset);
        offset += 2;
        SysLib.short2bytes(flag, data, offset);
        offset += 2;
        for (int i = 0; i < directSize; i++) {
            SysLib.short2bytes(direct[i], data, offset);
            offset += 2;
        }
        SysLib.short2bytes(indirect, data, offset);
        int block = 1 + num / 16;
        byte[] newData = new byte[Disk.blockSize];
        SysLib.rawread(block, newData);
        offset = num % 16 * iNodeSize;
        System.arraycopy(data, 0, newData, offset, iNodeSize);
        SysLib.rawwrite(block, newData);
    }
    // set index for block
    public boolean setIndexBlock(short index) {
        for (int i = 0; i < directSize; i++) {
            if (direct[i] == -1) {
                return false;
            }
        }
        if (indirect != -1) {
            return false;
        } else {
            indirect = index;
            byte[] data = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize / 2; i++) {
                SysLib.short2bytes((short) -1, data, i * 2);
            }
            SysLib.rawwrite(index, data);
            return true;
        }
    }
    // finds the block
    public int findBlock(int byteNum) {
        int blockNumber = byteNum / Disk.blockSize;
        if (blockNumber < directSize) {
            return direct[blockNumber];
        }
        if (indirect < 0) {
            return -1;
        }
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);
        int offset = (blockNumber - directSize) * 2;
        return (int) SysLib.bytes2short(data, offset); 
    }
    // submits the blocks into free space
    public int submitBlock(int pointer, short freeBlock) {
        int location = pointer / Disk.blockSize;
        if (location < directSize) {
            if (direct[location] >= 0)
                return IN_USE;
            if ((location > 0) && (direct[(location - 1)] == -1)) // alright to write
                return AVAILABLE;
            direct[location] = freeBlock; // update location
            return AVAILABLE;
        }
        if (indirect < 0) { // indirect empty
            return EMPTY;
        }
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);
        int offset = (location - directSize) * 2;
        if (SysLib.bytes2short(data, offset) > 0) { // in use
            return IN_USE;
        }
        SysLib.short2bytes(freeBlock, data, offset);
        SysLib.rawwrite(indirect, data);
        return AVAILABLE;
    }
    // frees indirect blocks
    public byte[] freeIndirect() {
        if (indirect >= 0) {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);
            indirect = -1;
            return data;
        } else {
            return null;
        }
    }
}