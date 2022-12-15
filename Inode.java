public class Inode {
    public static final int iNodeSize = 32; // fix to 32 bytes
    public static final int directSize = 11; // # direct pointers 
    public static final int IN_USE = -1;
    public static final int EMPTY = 0;
    public static final int AVAILABLE = 1;
    public int length; // file size in bytes
    public short count; // # file-table entries pointing to this
    public short flag; // 0 = unused, 1 = used, ...
    public short[] direct = new short[directSize]; // a indirect pointer
    public short indirect; // a indirect pointer
    // default constructor 
    public Inode() {
        length = 0;
        count = 0;
        flag = 0;
        for (int i = 0; i < directSize; i++) {
            direct[i] = -1;
        }
        indirect = -1;
    }
    
    // Overloaded Inode constructor
    // Inumber is is the identifier associated with this specific 
    // inode and sets all variables by reading from disk the values
    public Inode(short num) {
        // calcualte how many blocks to use 
        // (blocks are formated by size of 16)
        int blockNumber = 1 + num / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, data);
        // calculate how much to offset 
        int offset = (num % 16) * iNodeSize;
        // create space for variables
        length = SysLib.bytes2int(data, offset);
        offset += 4; // int offset
        count = SysLib.bytes2short(data, offset);
        offset += 2; // short offset
        flag = SysLib.bytes2short(data, offset);
        offset += 2;
        // allocate space for pointers
        for (int i = 0; i < directSize; i++) {
            direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
    }
    
    // Takes the passed in Inumber and that Inode from memory
    // and stores to disk returning the index of the block that 
    // its saved in.
    public void toDisk(short num) {
        // set buffer size
        byte[] data = new byte[iNodeSize];
        int offset = 0;
        SysLib.int2bytes(length, data, offset);
        offset += 4; // int offset
        SysLib.short2bytes(count, data, offset);
        offset += 2; // short offset
        SysLib.short2bytes(flag, data, offset);
        offset += 2;
        // allocate space for pointers
        for (int i = 0; i < directSize; i++) {
            SysLib.short2bytes(direct[i], data, offset);
            offset += 2;
        }
        SysLib.short2bytes(indirect, data, offset);
        // calcualte how many blocks to use 
        // (blocks are formated by size of 16)
        int block = 1 + num / 16;
        byte[] newData = new byte[Disk.blockSize];
        SysLib.rawread(block, newData);
        // calculate how much to offset 
        offset = num % 16 * iNodeSize;
        // copy Inode data into New Data arr
        System.arraycopy(data, 0, newData, offset, iNodeSize);
        // write NewData to disk
        SysLib.rawwrite(block, newData);
    }

    // set index for block
    public boolean setIndexBlock(short index) {
        for (int i = 0; i < directSize; i++) {
            if (direct[i] == -1) { // direct block doesn't exist
                return false;
            }
        }
        if (indirect != -1) { // indirect block doesn't exist
            return false;
        } else {
            indirect = index; // set indirect block to index
            byte[] data = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize / 2; i++) { 
                SysLib.short2bytes((short) -1, data, i * 2);
            }
            SysLib.rawwrite(index, data); // write index to data
            return true;
        }
    }
    // finds the block
    public int findBlock(int byteNum) {
        int blockNumber = byteNum / Disk.blockSize; // calculate block number
        if (blockNumber < directSize) { // blocknumber can't exceed size of directory
            return direct[blockNumber]; // return block 
        }
        if (indirect < 0) { // indirect block doesn't exists
            return -1;
        }
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);
        int offset = (blockNumber - directSize) * 2;
        return (int) SysLib.bytes2short(data, offset); 
    }
    // submits the blocks into free space
    public int submitBlock(int pointer, short freeBlock) {
        int location = pointer / Disk.blockSize; // calculate location for direct blocks based on pointer position
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
        SysLib.rawread(indirect, data); // read indirect block to data
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
            SysLib.rawread(indirect, data); // read indirect block to the data
            indirect = -1; // set to -1 
            return data; // return all free blocks
        } else {
            return null;
        }
    }
}
