public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int totalInodes;
    public int freeList;

    // SuperBlock constructor
    public SuperBlock(int diskSize) {
		// read superblock from disk	
        byte[] superBlock = new byte[Disk.blockSize];
		// superblock located in block zero
        SysLib.rawread(0, superBlock);

		// read total num of blocks
		totalBlocks = SysLib.bytes2int(superBlock,0);
		// read total num of inodes
		totalInodes = SysLib.bytes2int(superBlock,4);
		// read freeList
		freeList = SysLib.bytes2int(superBlock,8);
        
		//validate disk contents
        if ((totalBlocks == diskSize) && (totalInodes > 0) && (freeList >= 2)) {
			// disk contents are valid
            return;
        } else {
			// need to format disk
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }

	// Formats the disk (you implement)
	// sets the number of blocks that needs to be freed
    public void format(int inodeBlocks) {
		// sets the number of inodes that needs to be freed
        totalInodes = inodeBlocks;
		// create and write blank iNodes to disk
        for (short i = 0; i < totalInodes; i++) {
            Inode inode = new Inode();
            inode.flag = 0;
            inode.toDisk(i);
        }
        // sets freelist head to first free block
        freeList = 2 + totalInodes * 32 / Disk.blockSize;
		// create freelist linked list
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] superBlock = new byte[Disk.blockSize];
			// erase block
            for (int j = 0; j < Disk.blockSize; j++) {
                superBlock[j] = 0;
            }
			// sets bytes to point to next free block
            SysLib.int2bytes(i + 1, superBlock, 0);
			// write block back to disk
            SysLib.rawwrite(i, superBlock);
        }
        sync();
    }

    // Writes back to disk the updated variables if any
    public void sync() {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, 0);
        SysLib.int2bytes(totalInodes, superBlock, 4);
        SysLib.int2bytes(freeList, superBlock, 8);
        SysLib.rawwrite(0, superBlock);
    }

    // Returns the index of the first free block from 
	// the freeList which is the top block. 
    public int getFreeBlock() {
        int index = freeList;
        if (index != -1) {
            // dummy block to store first free block
            byte[] superBlock = new byte[Disk.blockSize];
            // read free block from disk
            SysLib.rawread(index, superBlock);
            freeList = SysLib.bytes2int(superBlock, 0);

            SysLib.int2bytes(0, superBlock, 0);
            SysLib.rawwrite(index, superBlock);
        }
        return index;


    }

    // Adds a new free block to the back of freeList (FIFO). If the
	// blockNumber is valid block fits in dis params else returns 
    // false indicating block was not added
    public boolean returnBlock(int blockNumber) {
        // valid block
        if (blockNumber >= 0) {
            // block returned to free
            byte[] superBlock = new byte[Disk.blockSize];
            // erase blocks
            for (int i = 0; i < Disk.blockSize; i++) {
                superBlock[i] = 0;
            }
            //set next free block and write to disk
            SysLib.int2bytes(freeList, superBlock, 0);
            SysLib.rawwrite(blockNumber, superBlock);
            freeList = blockNumber;
            return true;
        }
        // invalid block returned, do nothing
        return false;
    }
}