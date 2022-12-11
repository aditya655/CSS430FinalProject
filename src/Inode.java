public class Inode {
	public final static int iNodeSize = 32;  // fixed to 32 bytes
    public final static int directSize = 11; // # direct pointers

    public final static int NoError              = 0;
    public final static int ErrorBlockRegistered = -1;
    public final static int ErrorPrecBlockUnused = -2;
    public final static int ErrorIndirectNull    = -3;

    public int length;                 // file size in bytes
    public short count;                // # file-table entries pointing to this
    public short flag;       // 0 = unused, 1 = used(r), 2 = used(!r), 
                             // 3=unused(wreg), 4=used(r,wreq), 5= used(!r,wreg)
    public short direct[] = new short[directSize]; // directo pointers
    public short indirect;                         // an indirect pointer
	public short iNumber;

    Inode ( ) {                        // a default constructor
	length = 0;
	count = 0;
	flag = 1;
	for ( int i = 0; i < directSize; i++ )
	    direct[i] = -1;
	indirect = -1;
    }

	// making inode from disk
	Inode ( short iNumber ) {   
		this.iNumber = iNumber;               
		int blkNumber = 1 + iNumber / 16;          // inodes start from block#1
		byte[] data = new byte[Disk.blockSize]; 
		SysLib.rawread( blkNumber, data );         // get the inode block
		int offset = ( iNumber % 16 ) * iNodeSize; // locate the inode top

		length = SysLib.bytes2int( data, offset ); // retrieve all data members
		offset += 4;                               // from data
		count = SysLib.bytes2short( data, offset );
		offset += 2;
		flag = SysLib.bytes2short( data, offset );
		offset += 2;
		for ( int i = 0; i < directSize; i++ ) {
			direct[i] = SysLib.bytes2short( data, offset );
			offset += 2;
		}
		indirect = SysLib.bytes2short( data, offset );
		offset += 2;

		/*
		System.out.println( "Inode[" + iNumber + "]: retrieved " +
					" length = " + length +
					" count = " + count +
					" flag = " + flag +
					" direct[0] = " + direct[0] +
					" indirect = " + indirect );
		*/
    }

 	
	// saving this inode to disk
	void toDisk( short iNumber ) {  
		int blkNumber = 1 + iNumber / 16 ;
		byte[] data = new byte[Disk.blockSize];
		SysLib.rawread(blkNumber,data);   
		int offset = ( iNumber % 16 ) * iNodeSize; 

		length = SysLib.bytes2int( data, offset ); // retrieve all data members
		offset += 4;                               // from data
		count = SysLib.bytes2short( data, offset );
		offset += 2;
		flag = SysLib.bytes2short( data, offset );
		offset += 2;

		for ( int i = 0; i < directSize; i++ ) {
			direct[i] = SysLib.bytes2short( data, offset );
			offset += 2;
		}
		indirect = SysLib.bytes2short( data, offset );

		//offset += 2;

		SysLib.rawread(blkNumber,data); 

		// you implement
	}

	public int addBlock(short block){

		for(int i = 0; i < directSize; i++){
			if(direct[i] <= 0){
				direct[i] = block;
				return 0;
			}
		}

		if(indirect != -1){
			byte[] data = new byte[Disk.blockSize];
			SysLib.rawread(indirect,data);
			for(short offset = 0; offset < Disk.blockSize; offset += 2){
				if(SysLib.bytes2short(data,offset) <= 0){
					SysLib.short2bytes(block,data,offset);
					return SysLib.rawwrite(indirect,data);
				}
			}
		}
		return -1;
	}

	public short blockFromSeekPtr(int seekPtr){
		if(seekPtr < 0)
			return -1;
		
		short seekBlock = (short)(seekPtr / Disk.blockSize);

		if(seekBlock < directSize){
			short directBlock = direct[seekBlock];
			if(directBlock == -1) return -1;
			return directBlock;
		}

		else{
			if(indirect == -1) return -1;

			byte[] data = new byte[Disk.blockSize];
			SysLib.rawread(indirect,data);

			short inDirectBlock = SysLib.bytes2short(data, (seekBlock - directSize) * 2);
			if(inDirectBlock == 0) return -1;
      return inDirectBlock;
		}
   
	}
}
