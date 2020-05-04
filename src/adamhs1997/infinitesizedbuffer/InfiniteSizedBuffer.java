package adamhs1997.infinitesizedbuffer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class InfiniteSizedBuffer implements AutoCloseable {

    private double[] buffer;     // array to back the buffer
    private int headPtr;         // current position in true buffer array
    private int maxHeadPtr;      // current maximum number of values in buffer
    private int bufferSize;      // number of elements in buffer
    private int fileCtr;         // number of data files
    private int maxFileCtr;      // curent last file block on disk
    private boolean currentOut;  // determine if current data is out of memory
    private final String FNAME_BASE;     // base dir path to bin files

    InfiniteSizedBuffer(int inMemSize, String fnameBase) {
        buffer = new double[inMemSize];
        headPtr = 0;
        bufferSize = 0;
        fileCtr = 0;
        maxFileCtr = 0;
        FNAME_BASE = fnameBase;

        // Create parent directory if it doesn't exist
        File f = new File(FNAME_BASE);
        if (!f.getParentFile().exists()) {
            f.mkdirs();
        }
    }

    InfiniteSizedBuffer(int inMemSize) {
        this(inMemSize, "data_bin/buf_data");
    }

    public void writeData(double data) {
        // If most current data is not in memory, load it back in
        if (currentOut) {
            loadBlock(FNAME_BASE + "x.bin");
            currentOut = false;

            // Reset file counter to last block
            fileCtr = maxFileCtr;
        }

        // Write given data
        buffer[headPtr] = data;
        bufferSize++;
        maxHeadPtr++;

        if (++headPtr == buffer.length) {
            dumpData();
        }
    }

    public double readData() {
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
            int stopIdx = buffer.length;
            for (int i = 0; i < stopIdx; i++) {
                dos.writeDouble(buffer[i]);
            }

            // Move any data at end of array to start
            headPtr = 0;
            maxHeadPtr = headPtr;
            for (int i = stopIdx; i < buffer.length; i++) {
                buffer[i - stopIdx] = buffer[i];
            }

            // Incrememnt max file counter
            maxFileCtr++;
        } catch (IOException exc) {
            System.err.println("Failed to dump data!");
        }
    }

    private void writeInMemData() {
        try (DataOutputStream dos = new DataOutputStream(
          new FileOutputStream(FNAME_BASE + "x.bin"))) {
            // Write out anything in the buffer
            for (int i = 0; i < maxHeadPtr; i++) {
                dos.writeDouble(buffer[i]);
            }
        } catch (IOException exc) {
            System.err.println("Failed to dump data!");
        }
    }

    private void loadBlock(String blockName) {
        long s = System.currentTimeMillis();
        try (DataInputStream dis = new DataInputStream(
          new FileInputStream(blockName))) {
            headPtr = 0;
            maxHeadPtr = 0;
            while (true) {
                try {
                    buffer[headPtr] = dis.readDouble();
                    headPtr++;
                } catch (EOFException e) {
                    break;
                }
            }
            maxHeadPtr = headPtr;
        } catch (IOException e) {
            System.err.println("Could not read data!");
        }
        System.out.println("load " + (System.currentTimeMillis() - s));
    }

    public static void main(String[] args) {
        try (InfiniteSizedBuffer isb = new InfiniteSizedBuffer(1000000)) {
            int bufSize = 10000000;
            // Write into buffer
            for (int i = 0; i < bufSize; i++) {
                isb.writeData(i);
//                System.out.println(Arrays.toString(isb.buffer));

                // Simulate some reads interrupting the write stream
//                if (i == 23) {
//                    for (int j = 0; j < 7; j++) {
//                        System.out.println(isb.readData());
//                    }
//                    System.out.println("----");
//                }
//
//                if (i == 44) {
//                    for (int j = 0; j < 23; j++) {
//                        System.out.println(isb.readData());
//                    }
//                    System.out.println("----");
//                }
            }

            // Recover all data from buffer
            for (int i = isb.getBufferSize(); i > 0; i--) {
//                System.out.println(isb.readData());
                isb.readData();
            }
        }
    }
}
