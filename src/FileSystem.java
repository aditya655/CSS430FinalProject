import java.util.*;

public class FileSystem {
    private Superblock superblock;
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
        FileTableEntry e = filetable.falloc(filename,mode);

        if(mode.equals("r")){

        }

        else if(mode.equals("w")){

        }

        else if(mode.equals("w+")){

        }

        else{

        }
         
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

        ftEnt = new FileTableEntry();
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

    int write( FileTableEntry ftEnt, byte[] buffer ) {
        // at this point, ftEnt is only the one to modify the inode
        if ( ftEnt.mode == "r" )
            return -1;
    
        synchronized ( ftEnt ) {
            int offset   = 0;              // buffer offset
            int left     = buffer.length;  // the remaining data of this buffer
    

        }
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

       ftEnt.inode.toDisk(ftEnt.inode.iNumber);

       for(int i = 0; i < freedBlocks.size(); i++)
         superblock.returnBlock((int)freedBlocks.elementAt(i));

        return true;
    }

	
	
	
    boolean delete( String filename ) {
        FileTableEntry ftEnt = open( filename, "w" );
        short iNumber = ftEnt.iNumber;
        return close( ftEnt ) && directory.ifree( iNumber );
    }

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    int seek( FileTableEntry ftEnt, int offset, int whence ) {
        synchronized ( ftEnt ) {
            /*
            System.out.println( "seek: offset=" + offset +
                    " fsize=" + fsize( ftEnt ) +
                    " seekptr=" + ftEnt.seekPtr +
                    " whence=" + whence );
            */
			
		}

    }
}
