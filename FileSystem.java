public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    // constructor for the file system.
    public FileSystem(int diskBlocks) {
        superblock = new SuperBlock(diskBlocks);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);
        FileTableEntry entry = open("/", "r");
        int dirSize = fsize(entry);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(entry, dirData);
            directory.bytes2directory(dirData);
        }
        close(entry);
    }

    public void sync() {
        byte[] data = directory.directory2bytes();
        //open the root directory
        FileTableEntry entry = open("/", "w");
        write(entry, data);
        close(entry);
        superblock.sync();
    }
    // erases all the information in the disk
    public boolean format(int files) {
        superblock.format(files);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);
        return true;
    }
    //The read function checks if the a given block is valid to read from.
    public int read(FileTableEntry entry, byte[] buffer) {
        if (entry == null || (entry.mode == "a") || (entry.mode == "w")) {
            return -1;
        }
        int bufferIndex = 0;
        int remainingBuffer = buffer.length;
        int fileSize = fsize(entry);
        synchronized (entry) {
            while (remainingBuffer > 0 && entry.seekPtr < fileSize) {
                int blockNumber = entry.inode.findBlock(entry.seekPtr);
                if (blockNumber == -1) {
                    return bufferIndex;
                }
                byte[] blockData = new byte[Disk.blockSize];
                SysLib.rawread(blockNumber, blockData);
                int offset = entry.seekPtr % Disk.blockSize;
                int blockReadLength = Disk.blockSize - offset;
                int fileReadLength = fileSize - entry.seekPtr;
                int readLength = Math.min(Math.min(blockReadLength, remainingBuffer), fileReadLength);
                System.arraycopy(blockData, offset, buffer, bufferIndex, readLength);
                remainingBuffer -= readLength;
                entry.seekPtr += readLength;
                bufferIndex += readLength;
            }
            return bufferIndex;
        }
    }
    // write function for the file system
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
                if (SysLib.rawread(currentBlock, data) == -1) {
                    System.exit(2);
                }
                int diskLocation = entry.seekPtr % Disk.blockSize;
                int adjustedLocation = Disk.blockSize - diskLocation;
                int length = Math.min(adjustedLocation, buffLength);
                System.arraycopy(buffer, location, data, diskLocation, length);
                SysLib.rawwrite(currentBlock, data);
                entry.seekPtr += length;
                location += length;
                buffLength -= length;
                if (entry.seekPtr > entry.inode.length) {
                    entry.inode.length = entry.seekPtr;
                }
            }
            entry.inode.toDisk(entry.iNumber);
            return location;
        }
    }
    // seeker for the file system.
    public int seek(FileTableEntry entry, int offset, int location) {
        synchronized (entry) {
			switch(location) {
				case 0:
					entry.seekPtr = offset;
					break;
				case 1:
					entry.seekPtr += offset;
					break;
				case 2:
					entry.seekPtr = entry.inode.length + offset;
					break;
				default:
					return -1;
			}
			if (entry.seekPtr < 0) {
				entry.seekPtr = 0;
			}
			if (entry.seekPtr > entry.inode.length) {
				entry.seekPtr = entry.inode.length;
			}
			return entry.seekPtr;
		}
    }
    // helper to alert of invalide offset
    private void invalidOffset() {
        SysLib.cerr("invalid offset");
    }

    // The FileTableEntry method opens the specified file passed into it.
    public FileTableEntry open(String fileName, String mode) {
        FileTableEntry entry = filetable.falloc(fileName, mode);
        if (entry != null && mode == "w" && !deallocAllBlocks(entry)) { // no place to write
            return null;
        }
        return entry;
    }
    // The close function closes the file mapped to the passed in file table entry
    public boolean close(FileTableEntry entry) {
        synchronized (entry) {
            entry.count--;
            if (entry.count > 0) {
                return true;
            }
        }
        return filetable.ffree(entry);
    }

  // The delete function deletes the passed in file
    public boolean delete(String fileName) {
        FileTableEntry entry = open(fileName, "w");
        if (entry == null) {
            return false;
        }
        return close(entry) && directory.ifree(entry.iNumber);
    }
    // deallocates all blocks in the file system.
    private boolean deallocAllBlocks(FileTableEntry entry) {
        if (entry.inode.count != 1 || entry == null) {
            return false;
        }
        byte[] releasedBlocks = entry.inode.freeIndirect();
        if (releasedBlocks != null) {
            int num = SysLib.bytes2short(releasedBlocks, 0);
            while (num != -1) {
                superblock.returnBlock(num);
            }
        }
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