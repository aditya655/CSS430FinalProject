
public class Superblock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int inodeBlocks;
    public int freeList;
	
	// you implement
	public SuperBlock( int diskSize ) {
		// read the superblock from disk	
		byte[] superBlock = new byte[Disk.blockSize];
		SysLib.rawread(0, superBlock);
		totalBlocks = SysLib.bytes2int(superBlock,0);
		totalInodes = SysLib.bytes2int(superBlock,4);
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
	
	// you implement
	 void format( int files ) {
		// initialize the superblock
	 }
	
	// you implement
	public int getFreeBlock( ) {
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
