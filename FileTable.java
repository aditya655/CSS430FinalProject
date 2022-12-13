import java.util.*;

public class FileTable {
    public final static int UNUSED = 0;
    public final static int USED = 1;
    public final static int READ = 2;
    public final static int WRITE = 3;
    private ArrayList<FileTableEntry> tableList;
    private Directory dir;

    // create a file table of file table entries and set directory
    public FileTable(Directory dir) {
        tableList = new ArrayList<>();
        this.dir = dir;
    }

    //  allocates the file table
    public synchronized FileTableEntry falloc( String filename, String mode ) {
        short iNumber = -1;
        Inode inode = null;
        while (true) {
            iNumber = (filename.equals("/") ? (short) 0 : dir.namei(filename));
            if (iNumber >= 0) {
                inode = new Inode(iNumber);
                if (mode.equals("r")) {
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
                } else {
                    if (inode.flag == USED || inode.flag == UNUSED) {
                        inode.flag = WRITE;
                        break;
                    } else {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                }
            } else if (!mode.equals("r")) {
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = WRITE;
                break;
            } else {
                return null;
            }
        }
        inode.count++;
        inode.toDisk(iNumber);
        FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
        tableList.add(entry);
        return entry;
    }
    // frees up the file table for the provided entry
    public synchronized boolean ffree(FileTableEntry entry) {
        if (tableList.remove(entry)) {
            entry.inode.count--;
            entry.inode.flag = 0;
            entry.inode.toDisk(entry.iNumber);
            entry = null;
            notify();
            return true;
        }
        return false;
    }
    // empties the entire table.
    public synchronized boolean fempty() {
        return tableList.isEmpty();
    }
}