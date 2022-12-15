public class Directory {
    private static int maxChars = 30; // max characters of each file name
    private int fsizes[]; // each element stores a different file size
    private char fnames[][]; // each element stores a different file name
    private int dirSize; // size of the directory

    // Directory constructor
    public Directory(int maxNum) {
        fsizes = new int[maxNum]; // max files
        for (int i = 0; i < maxNum; i++) {
            fsizes[i] = 0; // all file size initialized to 0
        }
        fnames = new char[maxNum][maxChars];
        dirSize = maxNum;  // set
        String root = "/";
        fsizes[0] = root.length( );
        root.getChars( 0, fsizes[0], fnames[0], 0 );
    }
    // assumes data[] received directory information from disk
    // initializes the Directory instance with this data[]
    public void bytes2directory(byte data[]) {
        int offset = 0;
        for (int i = 0; i < dirSize; i++) {
            fsizes[i] = SysLib.bytes2int(data, offset);
            offset += 4;  // adjusting for block
        }
        for (int i = 0; i < dirSize; i++) {
            String tempName = new String(data, offset, maxChars * 2);
            tempName.getChars(0, fsizes[i], fnames[i], 0);
            offset += maxChars * 2;
        }
    }
    // converts and return Directory information into a plain byte array
    // this byte array will be written back to disk
    // note: only meaningfull directory information should be converted
    // into bytes
    public byte[] directory2bytes() {
        byte[] newDir = new byte[64 * (dirSize)];
        int offset = 0;
        for (int i = 0; i < dirSize; i++) {
            SysLib.int2bytes(fsizes[i], newDir, offset);
            offset += 4;
        }
        for (int i = 0; i < dirSize; i++) {
            String name = new String(fnames[i], 0, fsizes[i]);
            byte[] bytes = name.getBytes();
            System.arraycopy(bytes, 0, newDir, offset, bytes.length);
            offset += maxChars * 2;
        }
        return newDir;
    }
    // filename is the one of a file to be created
    // allocates a new inode number for this filename
    public short ialloc(String fileName) {
        for (int i = 1; i < dirSize; i++) {
            if (fsizes[i] == 0) {
                fsizes[i] = Math.min(fileName.length(), maxChars);
                fileName.getChars(0, fsizes[i], fnames[i], 0);
                return (short)i;
            }
        }
        return -1;
    }
    // deallocates this inumber (inode number)
    // the corresponding file will be deleted
    public boolean ifree(short num) {
        if (fsizes[num] > 0) { // check 
            fsizes[num] = 0;
            return true;
        } else {
            return false;
        }
    }
    // returns the inumber corresponding to this filename
    public short namei(String fileName) {
        for (int i = 0; i < dirSize; i++) {
            String tempName = new String(fnames[i], 0, fsizes[i]);
            if (fileName.equals(tempName)) {
                return (short)i;
            }
        }
        return -1;
    }
}
