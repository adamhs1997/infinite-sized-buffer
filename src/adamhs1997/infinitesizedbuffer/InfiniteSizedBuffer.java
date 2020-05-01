package adamhs1997.infinitesizedbuffer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/*
Create buffer of fixed size
Whenever buffer gets within x% of its actual size, write that back out to a file
When rewinding, we must dump out current data when we get back to the start of it

When buffering new data:
--As soon as head ptr passes x% threshold, have bkgd thread go thorugh and write that data.
--Lock the buffer to prevent new writes, then move new data to start
--Reset head ptr to end of current data, overwrite leftover data in array

Remember to delete log file upon exit or restart!!!--Don't want it to sit too long on device
 */
public class InfiniteSizedBuffer {

    // Create the buffer
    static double[] d = new double[10];
    static int headPtr = 0;
    static int globalSize = 0;
    static double dumpPoint = 0.9; // %
    static int bufSize = 50; // Should be (effectively) INF in the production code to support arbitrary length buffer
    static String fnameBase = "balls";
    static int fileCtr = 0;

    public static void main(String[] args) throws FileNotFoundException, IOException {
        // Write into buffer
        for (int i = 0; i < bufSize; i++) {
            d[headPtr] = i;
            globalSize++;
            if (++headPtr > dumpPoint * d.length) {
                dumpData();
            }
        }

        fileCtr--;
        // Read buffer back, reading file data when we hit uncached stuff
        for (int k = globalSize; k > 0; k--) {
            // Retrieve more data when we need it
            if (headPtr == 0) {
                DataInputStream dis = new DataInputStream(
                    new FileInputStream(fnameBase + fileCtr-- + ".bin"));
                while (true) {
                    try {
                        d[headPtr] = dis.readDouble();
                        headPtr++;
                    } catch (EOFException e) {
                        break;
                    }
                }
                dis.close();
            }

            System.out.println(d[headPtr - 1]);

            headPtr--;
        }

        // Clean up files
        new File(".").listFiles(f -> {
            if (f.getName().endsWith(".bin")) f.delete();
            return true;
        });
    }

    private static void dumpData() throws IOException {
        // Dump the data in the buffer to the file
        DataOutputStream dos = new DataOutputStream(new FileOutputStream(
                fnameBase + fileCtr++ + ".bin"));
        int stopIdx = (int) (dumpPoint * d.length);

        for (int i = 0; i < stopIdx; i++) {
            dos.writeDouble(d[i]);
        }

        // Move any data at end of array to start
        headPtr = d.length - stopIdx;
        for (int i = stopIdx; i < d.length; i++) {
            d[i - stopIdx] = d[i];
        }
        dos.close();
    }
}
