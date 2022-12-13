public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int totalInodes;
    public int freeList;
    // SuperBlock constructor
    public SuperBlock(int diskSize) {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        totalInodes = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);
        if ((totalBlocks == diskSize) && (totalInodes > 0) && (freeList >= 2)) {
            return;
        } else {
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }
    // formats the block
    public void format(int inodeBlocks) {
        totalInodes = inodeBlocks;
        for (short i = 0; i < totalInodes; i++) {
            Inode inode = new Inode();
            inode.flag = 0;
            inode.toDisk(i);
        }
        freeList = 2 + totalInodes * 32 / Disk.blockSize;
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] superBlock = new byte[Disk.blockSize];
            for (int j = 0; j < Disk.blockSize; j++) {
                superBlock[j] = 0;
            }
            SysLib.int2bytes(i + 1, superBlock, 0);
            SysLib.rawwrite(i, superBlock);
        }
        sync();
    }
    // synchronizes the superblock
    public void sync() {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, 0);
        SysLib.int2bytes(totalInodes, superBlock, 4);
        SysLib.int2bytes(freeList, superBlock, 8);
        SysLib.rawwrite(0, superBlock);
    }
    // returns a free block
    public int getFreeBlock() {
        int index = freeList;
        if (index != -1) {
            byte[] superBlock = new byte[Disk.blockSize];

            SysLib.rawread(index, superBlock);
            freeList = SysLib.bytes2int(superBlock, 0);

            SysLib.int2bytes(0, superBlock, 0);
            SysLib.rawwrite(index, superBlock);
        }
        return index;
    }
    // returns a block specified by block number
    public boolean returnBlock(int blockNumber) {
        if (blockNumber >= 0) {
            byte[] superBlock = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize; i++) {
                superBlock[i] = 0;
            }
            SysLib.int2bytes(freeList, superBlock, 0);
            SysLib.rawwrite(blockNumber, superBlock);
            freeList = blockNumber;
            return true;
        }
        return false;
    }
}