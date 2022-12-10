
public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int totalInodes;
    public int inodeBlocks;
    public int freeList;
	
	// SuperBlock constructor (you implement)
	// Reads the Superblock from the disk and initialize
	// variables from the number of blocks, inodes, 
	// and block number of the freeLists head.
	public SuperBlock( int diskSize ) {
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
		if(totalBlocks == diskSize && totalInodes > 0 && freeList >= 2){
			// disk contents are valid
			return;
		}
		else{
			// need to format disk
			totalBlocks = diskSize;
			format(defaultInodeBlocks);
		}
	}
	
	//  helper function
	void sync( ) {
		byte[] superBlock = new byte[Disk.blockSize];
		SysLib.rawread(0,superBlock);
		SysLib.int2bytes( totalBlocks, superBlock, 0 );
		SysLib.int2bytes( inodeBlocks, superBlock, 4 );
		SysLib.int2bytes( freeList, superBlock, 8 );
		SysLib.rawwrite( 0, superBlock );
		SysLib.cerr( "Superblock synchronized\n" );
    }

    void format( ) {
		// default format with 64 inodes
		format( defaultInodeBlocks );
    }
	
	
	// Formats the disk (you implement)
	// sets the number of blocks that needs to be freed
	void format( int files ) {

		// sets the number of inodes that needs to be freed
		totalInodes = files; 

		// create and write blank iNodes to disk
		for(short i = 0; i < totalInodes; i++) {
            Inode newInode = new Inode();
            newInode.flag = 0;
			// save to disk
            newInode.toDisk(i);
        }

		// sets freelist head to first free block, which is 
		// after all the inodes are created (16 per block) 
        freeList = (totalInodes / 16) + 2;

		// create freelist linked list
        for(int i = freeList; i < totalBlocks; i++) {
            byte[] block = new byte[Disk.blockSize];

			// erase block
            for(int j = 0; j < Disk.blockSize; j++) {
                block[j] = 0;
            }
			// sets bytes to point to next free block
            SysLib.int2bytes(i + 1, block, 0);
			// write block back to disk
            SysLib.rawwrite(i, block);
        }

		// update variables and read to disk changes
        this.sync();
	}
	
	// you implement
	public int getFreeBlock( ) {

		int result = freeList;
		byte[] data = new byte[Disk.blockSize];
		SysLib.rawread(freeList,data);

		freeList = SysLib.bytes2int(data,0);

		SysLib.int2bytes(0,data,0);
		SysLib.rawwrite(result,data);

		sync();
		return result;

		
		// get a new free block from the freelist
	}
	
	// you implement
	public boolean returnBlock( int oldBlockNumber ) {
		byte[] data = new byte[512];
		for(int i = 0; i < Disk.blockSize; i++){
			data[i] = 0;
		}

		SysLib.int2bytes(freeList,data,0);
		SysLib.rawwrite(oldBlockNumber,data);

		freeList = oldBlockNumber;
		return true;
	// return this former block
	}
	
}
