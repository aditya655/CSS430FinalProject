public class Directory {
    private static int maxChars = 30;
    private int fsizes[];
    private char fnames[][];
    private int dirSize;

    // Directory constructor
    public Directory(int maxNum) {
        fsizes = new int[maxNum];
        for (int i = 0; i < maxNum; i++) {
            fsizes[i] = 0;
        }
        fnames = new char[maxNum][maxChars];
        dirSize = maxNum;  // set
        String root = "/";
        fsizes[0] = root.length( );
        root.getChars( 0, fsizes[0], fnames[0], 0 );
    }
    // creates directory with size
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
    // returns bytes from directory
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
    // returns the file index when the file has nothing
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
    // frees up space in the file
    public boolean ifree(short num) {
        if (fsizes[num] > 0) {
            fsizes[num] = 0;
            return true;
        } else {
            return false;
        }
    }
    // sets name for the file.
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