public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    
    // constructor for the file system.
    public FileSystem(int diskBlocks) {
        // create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);
        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.totalInodes);
        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        // directory reconstruction
        FileTableEntry entry = open("/", "r");
        int dirSize = fsize(entry);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(entry, dirData);
            directory.bytes2directory(dirData);
        }
        close(entry);
    }
    
    // sync's the file system and superblock back to disk
    // in the root directory.  
    public void sync() {
        //open the root directory
        FileTableEntry entry = open("/", "w");
        // write directory to root "/"
        byte[] data = directory.directory2bytes();
        write(entry, data);
        // close root
        close(entry);
        // sync superblock
        superblock.sync();
    }
    
    // reformats all content of disk and resets the superblock, 
    // directory, and fileTable
    public boolean format(int files) {
        // reformats superblock with num of files or inodes
        // to be created by the superblock
        superblock.format(files);
        // creates directory and assigning "/" in driectory entry 0
        directory = new Directory(superblock.totalInodes);
        // stores directory in the file table
        filetable = new FileTable(directory);
        return true;
    }
    
    // Checks if the target block is valid to read from. 
    // If valid then read blocks and calculate the buffer data size
    // and read data in each loop based on the buffer size just calucated.
    // otherwise break and return -1
    public int read(FileTableEntry entry, byte[] buffer) {
        // invalid modes check
        if (entry == null || (entry.mode == "a") || (entry.mode == "w")) {
            return -1;
        }
        int bufferIndex = 0;                    // track data being read
        int remainingBuffer = buffer.length;    // size of data to read
        int fileSize = fsize(entry);         

        synchronized (entry) {
            // loop to read data
            while (remainingBuffer > 0 && entry.seekPtr < fileSize) {
                // set up variable to read data from block if valid
                int blockNumber = entry.inode.findBlock(entry.seekPtr);
                if (blockNumber == -1) {
                    return bufferIndex;
                }
                // read data block
                byte[] blockData = new byte[Disk.blockSize];
                SysLib.rawread(blockNumber, blockData);
                // set ptr to read block data
                int offset = entry.seekPtr % Disk.blockSize;
                // read whats left in block
                int blockReadLength = Disk.blockSize - offset;
                // check remaining length of file left
                int fileReadLength = fileSize - entry.seekPtr;
                int readLength = Math.min(Math.min(blockReadLength, remainingBuffer), fileReadLength);
                // copy data read to buffer
                System.arraycopy(blockData, offset, buffer, bufferIndex, readLength);

                // updates variables for next loop
                remainingBuffer -= readLength;
                entry.seekPtr += readLength;
                bufferIndex += readLength;
            }
            return bufferIndex;
        }
    }
    
    // Writes data within buffer to file given by the entry, starting at the seekptr
    // position. seekPtr is incremented by the length of bytes to be written. The
    // return value is the number of bytes that have been written. Otherwise return -1
    // for errors. 
    public int write(FileTableEntry entry, byte[] buffer) {
        int location = 0;
        if (entry == null || entry.mode == "r") {
            return -1;
        }
        synchronized (entry) {
            int buffLength = buffer.length;
            while (buffLength > 0) { // loop over buffer
                int currentBlock = entry.inode.findBlock(entry.seekPtr); // try to find the given block
                if (currentBlock == -1) { // need find a free block
                    short freeBlock = (short) superblock.getFreeBlock();
                    // attempt to submit block, then act based on return code
                    int status = entry.inode.submitBlock(entry.seekPtr, freeBlock);
                    switch ( status )
                    // 1 = good to write, -1 = in use, 0 = indirect is empty
                    {
                        case Inode.IN_USE:
                            SysLib.cerr("Filesystem error on write\n");
                            return -1;
                        case Inode.EMPTY: // indirect is empty, search for new location
                            freeBlock = (short) superblock.getFreeBlock();
                            status = entry.inode.submitBlock(entry.seekPtr, freeBlock); // attempt to submit location
                            if (!entry.inode.setIndexBlock((short) status)) { // attempt to set index to new location
                                SysLib.cerr("Filesystem error on write\n");
                                return -1;
                            }
                            // attempt submit block again
                            if ( entry.inode.submitBlock(entry.seekPtr, freeBlock) != Inode.AVAILABLE ) {
                                SysLib.cerr("Filesystem error on write\n");
                                return -1;
                            }
                            break;
                    }
                    // update location
                    currentBlock = freeBlock;
                }

                byte[] data = new byte[Disk.blockSize];
                // check if valid block
                if (SysLib.rawread(currentBlock, data) == -1) {
                    System.exit(2);
                }
                // walk through file
                int diskLocation = entry.seekPtr % Disk.blockSize;
                // calculate size diff between blocks
                int adjustedLocation = Disk.blockSize - diskLocation;
                int length = Math.min(adjustedLocation, buffLength);

                // copy remaining blocks to array
                System.arraycopy(buffer, location, data, diskLocation, length);
                SysLib.rawwrite(currentBlock, data);
                
                // update variables for next loop
                entry.seekPtr += length;
                location += length;
                buffLength -= length;

                // update inode length if seekptr larger
                if (entry.seekPtr > entry.inode.length) {
                    entry.inode.length = entry.seekPtr;
                }
            }
            // save inode to disk
            entry.inode.toDisk(entry.iNumber);
            return location;
        }
    }
    
    // updates seekPtr realative to passed in entry. Returning the 
    // new position of seekPtr
    public int seek(FileTableEntry entry, int offset, int location) {
        synchronized (entry) {
			switch(location) {
                
				case 0: // beginning of file
					entry.seekPtr = offset;
					break;
               
				case 1: // current position of ptr in file
					entry.seekPtr += offset;
					break;
                
				case 2: // end of file
					entry.seekPtr = entry.inode.length + offset;
					break;
				default:
					return -1;
			}
            // Tries to set set ptr to negative in this case
            // set it to zero
			if (entry.seekPtr < 0) {
				entry.seekPtr = 0;
			}
            // Tries to set pointer out of range of file size in this
            // case we set it to end of file
			if (entry.seekPtr > entry.inode.length) {
				entry.seekPtr = entry.inode.length;
			}
            // return success
			return entry.seekPtr;
		}
    }
    
    // helper to alert of invalide offset
    private void invalidOffset() {
        SysLib.cerr("invalid offset");
    }


    // Opens files by passed in name and what mode that fileName
    // object will have once created and returned from falloc method. 
    public FileTableEntry open(String fileName, String mode) {
        FileTableEntry entry = filetable.falloc(fileName, mode);
        // checks if mode is w and valid to delete all blocks and start
        // writing from scratch
        if (entry != null && mode == "w" && !deallocAllBlocks(entry)) { // no place to write
            return null;
        }
        // new entry is passed ensuring that its not empty if it was 'w'
        return entry;
    }
    
    // The close function closes the file mapped to the 
    // passed in file table entry
    public boolean close(FileTableEntry entry) {
        synchronized (entry) {
            // decrease num of users which use that entry
            entry.count--;
            // users still using entry return success
            if (entry.count > 0) {
                return true;
            }
        }
        // if no more users using the entry free 
        // file entry in fileTable
        return filetable.ffree(entry);
    }

    // deletes file given by passed in fileName. 
    public boolean delete(String fileName) {
        // opens the file containing Inode object
        FileTableEntry entry = open(fileName, "w");
        // not successful opening of Inode (entry)
        if (entry == null) {
            return false;
        }
        // having access to Inodes variables we free it from Directory 
        // with its inumber and close it afterwards. returning 
        // the success of these actions
        return close(entry) && directory.ifree(entry.iNumber);
    }
    
    // checks if entry is valid, if so handles indirect pointers calling 
    // return block from superblock. Then goes through all the direct
    // pointrs and calls superblock to return if valid. Deallocating
    // pointers 
    private boolean deallocAllBlocks(FileTableEntry entry) {
        // return Inode if invalid
        if (entry.inode.count != 1 || entry == null) {
            return false;
        }
        // handle indirect pointers
        byte[] releasedBlocks = entry.inode.freeIndirect();
        if (releasedBlocks != null) {
            int num = SysLib.bytes2short(releasedBlocks, 0);
            while (num != -1) {
                superblock.returnBlock(num);
            }
        }
        // handle direct pointers
        for (int i = 0; i < Inode.directSize; i++)
            if (entry.inode.direct[i] != -1) {
                superblock.returnBlock(entry.inode.direct[i]);
                entry.inode.direct[i] = -1;
            }
        entry.inode.toDisk(entry.iNumber);
        return true;
    }
    
    //The fsize funtion return the size of the specified file
    public int fsize(FileTableEntry entry) {
        synchronized (entry) {
            return entry.inode.length;
        }
    }
}
