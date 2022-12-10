import java.util.*;

public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    public FileSystem( int diskBlocks ) {
        // create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock( diskBlocks );
    
        // create directory, and register "/" in directory entry 0
        directory = new Directory( superblock.inodeBlocks );
    
        // file table is created, and store directory in the file table
        filetable = new FileTable( directory );
    
        // directory reconstruction
        FileTableEntry dirEnt = open( "/", "r" );
        int dirSize = fsize( dirEnt );
        if ( dirSize > 0 ) {
            byte[] dirData = new byte[dirSize];
            read( dirEnt, dirData );
            directory.bytes2directory( dirData );
        }
        close( dirEnt );
    }

    void sync( ) {
        // directory synchronizatioin
        FileTableEntry dirEnt = open( "/", "w" );
        byte[] dirData = directory.directory2bytes( );
        write( dirEnt, dirData );
        close( dirEnt );
    
        // superblock synchronization
        superblock.sync( );
    }

    boolean format( int files ) {
        // wait until all filetable entries are destructed
        while ( filetable.fempty( ) == false )
            ;
    
        // format superblock, initialize inodes, and create a free list
        superblock.format( files );
    
        // create directory, and register "/" in directory entry 0
        directory = new Directory( superblock.inodeBlocks );
    
        // file table is created, and store directory in the file table
        filetable = new FileTable( directory );
    
        return true;
    }

    FileTableEntry open( String filename, String mode ) {
        // filetable entry is allocated
        boolean newFile = directory.namei(filename) == -1;
        FileTableEntry e = filetable.falloc(filename,mode);

        if(mode.equals("r")){
            if(newFile)
               return null;
            e.seekPtr = 0;
        }

        else if(mode.equals("w")){
            e.seekPtr = 0;
            deallocAllBlocks(e);
            newFile = true;
        }

        else if(mode.equals("w+")){
            e.seekPtr = 0;
        }

        else{
            e.seekPtr = e.inode.length;
        }

        if(newFile){
            short directBlock = superblock.claimBlock();
            if(directBlock == -1)
                return null;
            
                e.inode.addBlock(directBlock);
                e.inode.toDisk(e.iNumber);
            
        }
        // return filetable entry
        return e;
    }

    boolean close( FileTableEntry ftEnt ) {
        // filetable entry is freed
        synchronized ( ftEnt ) {
            // need to decrement count; also: changing > 1 to > 0 below
            ftEnt.count--;
            if ( ftEnt.count > 0 ) // my children or parent are(is) using it
                return true;
        }
        return filetable.ffree( ftEnt );
    }
	
	

    int fsize( FileTableEntry ftEnt ) {

        
        return ftEnt.inode.length;

    }


    int read( FileTableEntry ftEnt, byte[] buffer ) {
        if ( ftEnt.mode == "w" || ftEnt.mode == "a" )
            return -1;
    
        int offset   = 0;              // buffer offset
        int left     = buffer.length;  // the remaining data of this buffer
    
        synchronized ( ftEnt ) {
			// repeat reading until no more data  or reaching EOF


        }
    }

    private synchronized int readFromFileTableEntry(FileTableEntry e, byte[] buffer){
        byte[] data = new byte[512];
        int readBytes = 0;
        int readLength = 0;
        int startCopy = 0;
        int iterate = 0;
        int block;

        while(readBytes < buffer.length && e.seekPtr < e.inode.length){

            iterate++;
            block = e.inode.blockFromSeekPtr(e.seekPtr);

            if(block == -1){
                return -1;
                
            }

            if(SysLib.rawread(block,data) == -1){
                return -1;
            }

            int offset = e.seekPtr % Disk.blockSize;
            int bytesAvail = Disk.blockSize - offset;


            int remainingBytes = e.inode.length - e.seekPtr;
            boolean lastBlock = remainingBytes <= bytesAvail;
            readLength = (lastBlock ? remainingBytes : bytesAvail);

            int bytesToRead = (buffer.length - readBytes) < readLength ? (buffer.length - readBytes) : readLength;

            System.arraycopy(data, offset, buffer, startCopy, bytesToRead);

            

            readBytes += bytesToRead;
            e.seekPtr += bytesToRead;
            startCopy += bytesToRead;

          
        }
      

        return readBytes;
    }

    int write( FileTableEntry ftEnt, byte[] buffer ) {
        // at this point, ftEnt is only the one to modify the inode
        if ( ftEnt.mode == "r" )
            return -1;
    
        synchronized ( ftEnt ) {
            int offset   = 0;              // buffer offset
            int left     = buffer.length;  // the remaining data of this buffer
    

        }
    }

    private synchronized int writeFromFileTableEntry(FileTableEntry e, byte[] buffer){
        short block;
        int blockOffSet = e.seekPtr % Disk.blockSize;
        byte[] data = new byte[Disk.blockSize];
        int writtenBytes = 0;
        int iterate = 0;

        while(writtenBytes < buffer.length && e.seekPtr < e.inode.length){
            iterate++;
            block = e.inode.blockFromSeekPtr(e.seekPtr);

            if(block == -1){
                int seekBlock = e.seekPtr / Disk.blockSize;
                if(seekBlock >= e.inode.directSize && e.inode.indirect <= 0){
                    short index = (short)superblock.getFreeBlock();
                    if(index == -1){
                        return -1;
                    }
                    e.inode.indirect = index; 
                }
               
                block = (short)superblock.getFreeBlock();
                if(block == -1){
                    return -1;
                }
                e.inode.addBlock(block);
                
            }

            int remainingBytes = buffer.length - writtenBytes;
            SysLib.rawread(block,data);
            int writeLength = ((remainingBytes < (Disk.blockSize - blockOffSet)) ? remainingBytes : (Disk.blockSize - blockOffSet));
            System.arraycopy(buffer, writtenBytes, data, blockOffSet, writeLength);

            SysLib.rawwrite(block,data);

            writtenBytes += writeLength;
            e.seekPtr += writeLength;

            blockOffSet = 0;
            
    }

        return writtenBytes;
    }

    private boolean deallocAllBlocks( FileTableEntry ftEnt ) {
        Vector<Short> freedBlocks = new Vector<Short>();
       
        for(int i = 0; i < ftEnt.inode.directSize; i++){
       
          if(ftEnt.inode.direct[i] > 0){
            freedBlocks.add(new Short(ftEnt.inode.direct[i]));
            ftEnt.inode.direct[i] = -1;
          }
          
         else if(ftEnt.inode.indirect >= 0){
            byte[] inDirectBlock = new byte[Disk.blockSize];
            SysLib.rawread(ftEnt.inode.indirect, inDirectBlock);

            for(int i = 0; i < inDirectBlock.length; i++ ){
                short validBlock = SysLib.bytes2short(inDirectBlock,i);

                if(validBlock > 0){
                    freedBlocks.add(new Short(validBlock));
                }
            }
            freedBlocks.add(new Short(ftEnt.inode.indirect));
            ftEnt.inode.indirect = -1;
         }
       }

       ftEnt.inode.toDisk(ftEnt.iNumber);

       for(int i = 0; i < freedBlocks.size(); i++)
         superblock.returnBlock((int)freedBlocks.elementAt(i));

        return true;
    }

	
	
	
    boolean delete( String filename ) {
        FileTableEntry ftEnt = open( filename, "w" );
        short iNumber = ftEnt.iNumber;
       
        
        while(ftEnt.inode.count > 0){
            try{
                wait();
            }
            catch
                (InterruptedException e){};
            
        }
        ftEnt.inode.count = 0;
        ftEnt.inode.length = 0;
        ftEnt.inode.flag = 0;
        deallocAllBlocks(ftEnt);
        return close( ftEnt ) && directory.ifree( iNumber );
    }

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

   
    int seek( FileTableEntry ftEnt, int offset, int whence ) {
    
        synchronized ( ftEnt ) {
            if(ftEnt == null) return -1;

            int seek = ftEnt.seekPtr;
            switch(whence){
                case SEEK_SET:
                 seek = offset;
                 break;
                case SEEK_CUR:
                 seek += offset;
                 break;
                 case SEEK_END:
                 seek += offset;
                 break;
                 default:
                  return -1;
                 
            }

            if(seek < 0) return -1;

            ftEnt.seekPtr = seek;
            return ftEnt.seekPtr;
            /*
            System.out.println( "seek: offset=" + offset +
                    " fsize=" + fsize( ftEnt ) +
                    " seekptr=" + ftEnt.seekPtr +
                    " whence=" + whence );
            */
			
		}

    }
}
