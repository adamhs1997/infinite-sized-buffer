package adamhs1997.infinitesizedbuffer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
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
 */
public class InfiniteSizedBuffer implements AutoCloseable {

    private double[] buffer;     // array to back the buffer
    private int headPtr;         // current position in true buffer array
    private int bufferSize;      // number of elements in buffer
    private double dumpPoint;    // percentage of size at which we begin dumping
    private int fileCtr;         // number of data files
    private boolean writeMode;   // determine if user is reading or writing
    private boolean currentOut;  // determine if current data is out of memory
    final String FNAME_BASE;     // base dir path to bin files

    InfiniteSizedBuffer(int inMemSize, double dumpPct, String fnameBase) {
        buffer = new double[inMemSize];
        headPtr = 0;
        bufferSize = 0;
        dumpPoint = dumpPct / 100;
        fileCtr = 0;
        FNAME_BASE = fnameBase;

        // Create parent directory if it doesn't exist
        File f = new File(FNAME_BASE);
        if (!f.getParentFile().exists()) {
            f.mkdirs();
        }
    }

    InfiniteSizedBuffer(int inMemSize, double dumpPct) {
        this(inMemSize, dumpPct, "data_bin/buf_data");
    }

    public static void main(String[] args) {
        try (InfiniteSizedBuffer isb = new InfiniteSizedBuffer(10, 90)) {
            int bufSize = 50;
            // Write into buffer
            for (int i = 0; i < bufSize; i++) {
                isb.writeData(i);
            }

            for (int i = isb.getBufferSize(); i > 0; i--) {
                System.out.println(isb.readData());
            }
        }
    }

    public void writeData(double data) {
        writeMode = true;

        // If most current data is not in memory, load it back in
        if (currentOut) {
            loadBlock(FNAME_BASE + "x.bin");
            currentOut = false;
        }

        // Write given data
        buffer[headPtr] = data;
        bufferSize++;

        if (++headPtr > dumpPoint * buffer.length) {
            dumpData();
        }
    }

    public double readData() {
        writeMode = false;

        // Retrieve more data when we need it
        if (headPtr == 0) {
            // If the current data is still in, must evict it
            if (!currentOut) {
                currentOut = true;
                writeInMemData();
                fileCtr--;
            }

            loadBlock(FNAME_BASE + fileCtr-- + ".bin");
        }

        // Update head pointer location
        headPtr--;

        return buffer[headPtr];
    }

    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public void close() {
        new File(FNAME_BASE).getParentFile().listFiles(f -> {
            if (f.getName().endsWith(".bin")) f.delete();
            return true;
        });
    }

    private void dumpData() {
        // Dump the data in the buffer to the file
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(
          FNAME_BASE + fileCtr++ + ".bin"))) {
            // Write out anything in the buffer
            int stopIdx = (int) (dumpPoint * buffer.length);
            for (int i = 0; i < stopIdx; i++) {
                dos.writeDouble(buffer[i]);
            }

            // Move any data at end of array to start
            headPtr = buffer.length - stopIdx;
            for (int i = stopIdx; i < buffer.length; i++) {
                buffer[i - stopIdx] = buffer[i];
            }
        } catch (IOException exc) {
            System.err.println("Failed to dump data!");
        }
    }

    private void writeInMemData() {
        try (DataOutputStream dos = new DataOutputStream(
          new FileOutputStream(FNAME_BASE + "x.bin"))) {
            // Write out anything in the buffer
            for (int i = 0; i < headPtr; i++) {
                dos.writeDouble(buffer[i]);
            }
        } catch (IOException exc) {
            System.err.println("Failed to dump data!");
        }
    }

    private void loadBlock(String blockName) {
        try (DataInputStream dis = new DataInputStream(
          new FileInputStream(blockName))) {
            headPtr = 0;
            while (true) {
                try {
                    buffer[headPtr] = dis.readDouble();
                    headPtr++;
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read data!");
        }
    }
}
