import java.util.*;

public class FileTable {
    // flag values
    public final static int UNUSED = 0;
    public final static int USED = 1;
    public final static int READ = 2;
    public final static int WRITE = 3;
    
    private ArrayList<FileTableEntry> tableList; // the actual entity of this file table
    private Directory dir; // the root directory

    // create a file table of file table entries and set directory
    public FileTable(Directory dir) {
        tableList = new ArrayList<>(); // instantiate a file (structure) table 
        this.dir = dir; // receive a reference to the Director from the file system
    }

    //  allocates a new file (structure) table entry for this file name
    // allocate/retrieve and register the corresponding inode using directory
    // immeadiately write back this inode to the disk
    // return a reference to this file (structure) table entry
    public synchronized FileTableEntry falloc( String filename, String mode ) {
        // set to -1 and null since nothing has been allocated yet
        short iNumber = -1;
        Inode inode = null;
        
        while (true) {
            iNumber = (filename.equals("/") ? (short) 0 : dir.namei(filename)); // done because we are using a single level root directory
            if (iNumber >= 0) { // id for Inode exists
                inode = new Inode(iNumber);
                if (mode.equals("r")) { // set flag to value for read if the mode of the file is read only
                    if (inode.flag == READ
                            || inode.flag == USED
                            || inode.flag == UNUSED) {
                        inode.flag = READ;
                        break;
                    } else if (inode.flag == WRITE) {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                } else { // mode is append 
                    if (inode.flag == USED || inode.flag == UNUSED) {
                        inode.flag = WRITE;
                        break;
                    } else {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                }
            } else if (!mode.equals("r")) { // mode isn't reading
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = WRITE;
                break;
            } else { // invalid node
                return null;
            }
        }
        // increae the count of inode, save it to the disk, and make a new entry and add it to the tablelist
        inode.count++;
        inode.toDisk(iNumber);
        FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
        tableList.add(entry);
        return entry;
    }
    // receive a file table entry reference
    // save the corresponding inode to the disk
    // free this file table entry
    // return true if this file table entry found in the table
    public synchronized boolean ffree(FileTableEntry entry) {
        if (tableList.remove(entry)) { // if the entry is removed
            entry.inode.count--;
            entry.inode.flag = 0;
            entry.inode.toDisk(entry.iNumber);
            entry = null;
            notify();
            return true;
        }
        return false;
    }
    // return if table is empty
    // should be called before starting a format
    public synchronized boolean fempty() {
        return tableList.isEmpty();
    }
}