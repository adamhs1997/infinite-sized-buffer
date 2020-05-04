package adamhs1997.infinitesizedbuffer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class InfiniteSizedBuffer implements AutoCloseable {

    private double[] buffer;     // array to back the buffer
    private int stopIdx;         // where copying in array stops during dump
    private int headPtr;         // current position in true buffer array
    private int maxHeadPtr;      // current maximum number of values in buffer
    private int bufferSize;      // number of elements in buffer
    private int fileCtr;         // number of data files
    private int maxFileCtr;      // curent last file block on disk
    private boolean currentOut;  // determine if current data is out of memory
    private final String FNAME_BASE;     // base dir path to bin files

    InfiniteSizedBuffer(int inMemSize, double pctInMem, String fnameBase) {
        buffer = new double[inMemSize];
        stopIdx = buffer.length - (int) ((pctInMem / 100) * buffer.length);
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

    InfiniteSizedBuffer(int inMemSize, double pctInMem) {
        this(inMemSize, pctInMem, "data_bin/buf_data");
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
            // Write out anything in the buffer up until the stop index
            for (int i = 0; i < stopIdx; i++) {
                dos.writeDouble(buffer[i]);
            }

            // Move any data at end of array to start
            headPtr = buffer.length - stopIdx;
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
    }

    public static void main(String[] args) {
        try (InfiniteSizedBuffer isb = new InfiniteSizedBuffer(10, 30)) {
            int bufSize = 100;
            // Write into buffer
            for (int i = 0; i < bufSize; i++) {
                isb.writeData(i);
                
                // Simulate some reads interrupting the write stream
                if (i == 23) {
                    for (int j = 0; j < 7; j++) {
                        System.out.println(isb.readData());
                    }
                    System.out.println("----");
                }

                if (i == 44) {
                    for (int j = 0; j < 23; j++) {
                        System.out.println(isb.readData());
                    }
                    System.out.println("----");
                }
            }

            // Recover all data from buffer
            for (int i = isb.getBufferSize(); i > 0; i--) {
                System.out.println(isb.readData());
            }
        }
    }
}
