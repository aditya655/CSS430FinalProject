import java.util.Vector;

public class FileTable {
// File Structure Table

    private Vector<FileTableEntry> table;// the entity of File Structure Table
    private Directory dir;         // the root directory
    
    public FileTable ( Directory directory ) {// a default constructor
	table = new Vector<FileTableEntry>( );// instantiate a file table
	dir = directory;                      // instantiate the root directory
    }

	
	// you implement
	// allocates new file table entry for filename, allocates and retrieves the 
	// indicated inode, and returns reference to file table entry
	public synchronized FileTableEntry falloc( String fname, String mode ) {
		// gets the iNumber from inode for passed in filename if it doesn't exist
		short iNumber = (fname.equals("/") ? 0 : dir.namei(fname));
		Inode inode = null;

		// flag 1 (UNUSED), flag 1 (USED), flag 2 (READ), flag 3 (WRITE)

		// loop because function synchronized 
		while (true) {
			// if inode exist 
			if (iNumber >= 0) {
				// gets file from disk
				inode = new Inode(iNumber);
				// if mode is read
				if (mode.equals("r")) {
					 
					// if no other threads are writing to it
					if (inode.flag != 3) {
						// change to read
						inode.flag = 2; 
						break;
					}
					// other threads are writing to the inode we must wait
					else {
						try {
							wait();
						} catch (Exception e) {	
							SysLib.cout("error in writing");
						}
					}
				}
				// if file is requesting: w, w/r, or a
				else {
					// if flag of file is used
					if (inode.flag == 0 || inode.flag == 1) {
						inode.flag = 3;
						break;
					}
					// other threads are reading or writing to the inode we must wait
					else {
						try {
							wait();
						}
						catch (Exception e) {  
							SysLib.cout("error in w, w+, a (when !r)");
						}
					}
				}
			} 
			// if inode doesn't exist in directory 
			// create a new inode 
			else if (!mode.equals("r")) {
				// alloc an INumber for file
				iNumber = dir.ialloc(fname);
				// create an inode for file
				inode = new Inode(iNumber);
				// new descriptor number 3 (has to be in range of 3-31)
				inode.flag = 3;
				break;
			}
			else {
				return null;
			} 
		}
		
		// increases count of users
		inode.count++;
		// save to disk
		inode.toDisk(iNumber);
		// create new file table entry and add to table
		FileTableEntry e = new FileTableEntry(inode, iNumber, mode);
		table.addElement(e);
		// return entry
		return e;
	}

    public synchronized boolean ffree( FileTableEntry e ) {
	// receive a file table entry
	// free the file table entry corresponding to this index
	if ( table.removeElement( e ) == true ) { // find this file table entry
	    e.inode.count--;       // this entry no longer points to this inode
	    switch( e.inode.flag ) {
	    case 1: e.inode.flag = 0; break;
	    case 2: e.inode.flag = 0; break;
	    case 4: e.inode.flag = 3; break;
	    case 5: e.inode.flag = 3; break;
	    }
	    e.inode.toDisk( e.iNumber );     // reflect this inode to disk
	    e = null;                        // this file table entry is erased.
	    notify( );
	    return true;
	} else
	    return false;
    }

    public synchronized boolean fempty( ) {
	return table.isEmpty( );             // return if table is empty
    }                                        // called before a format
}
}
