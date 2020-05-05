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
    private final String DIR_NAME_BASE;     // base dir path to bin files

    /**
     * Construct new InfiniteSizedBuffer object.
     * @param inMemSize Maximum number of buffer elements to keep in memory.
     * @param pctInMem Percent of elements to retain when data paged to disk.
     * @param dirNameBase Base directory name to write buffer files to.
     */
    InfiniteSizedBuffer(int inMemSize, double pctInMem, String dirNameBase) {
        buffer = new double[inMemSize];
        stopIdx = buffer.length - (int) ((pctInMem / 100) * buffer.length);
        headPtr = 0;
        bufferSize = 0;
        fileCtr = 0;
        maxFileCtr = 0;
        DIR_NAME_BASE = dirNameBase;

        // Create parent directory if it doesn't exist
        File f = new File(DIR_NAME_BASE);
        if (!f.getParentFile().exists()) {
            f.mkdirs();
        }
    }

    /**
     * Construct new InfiniteSizedBuffer object with default data directory.
     * @param inMemSize Maximum number of buffer elements to keep in memory.
     * @param pctInMem Base directory name to write buffer files to.
     */
    InfiniteSizedBuffer(int inMemSize, double pctInMem) {
        this(inMemSize, pctInMem, "data_bin/buf_data");
    }

    /**
     * Write an item into the buffer.
     * @param data Data item to write.
     */
    public void writeData(double data) {
        // If most current data is not in memory, load it back in
        if (currentOut) {
            loadBlock(DIR_NAME_BASE + "x.bin");
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

    /**
     * Write multiple items into the buffer.
     * @param data Array of data items to write into buffer.
     */
    public void writeMultiple(double[] data) {
        int amtWritten = 0;
        while (amtWritten != data.length) {
            int amtInsertable = Math.min(buffer.length - headPtr,
                    data.length - amtWritten);
            System.arraycopy(data, amtWritten, buffer, headPtr, amtInsertable);
            headPtr += amtInsertable;
            bufferSize += amtInsertable;
            maxHeadPtr += amtInsertable;
            amtWritten += amtInsertable;
            if (headPtr == buffer.length)
                dumpData();
        }
    }

    /**
     * Read an item from the buffer.
     * @return Newest item in the buffer.
     */
    public double readData() {
        // Retrieve more data when we need it
        if (headPtr == 0) {
            // If the current data is still in, must account for that in ctr
            if (!currentOut) fileCtr--;

            loadBlock(DIR_NAME_BASE + fileCtr-- + ".bin");
        }

        // Update head pointer location
        headPtr--;

        return buffer[headPtr];
    }

    public double[] readRange(int startIdx, int endIdx) {
        // Check if is valid range
        if (startIdx > endIdx)
            throw new IllegalArgumentException(
                    "Start index must be less than end index!");
        if (startIdx > bufferSize || endIdx > bufferSize)
            throw new IllegalArgumentException("Indices out of range!");

        // Create place to hold the data
        double[] result = new double[endIdx - startIdx];

        // Figure out starting block number
        int startBlock = startIdx / stopIdx;
        int startPos = startIdx % stopIdx;
        if (startBlock < maxFileCtr) {
            // Block is not in memory already
            loadBlock(DIR_NAME_BASE + startBlock + ".bin");
        } else if (currentOut) {
            // Means current data not in memory
            loadBlock(DIR_NAME_BASE + "x.bin");
            currentOut = false;
        }

        // Figure out ending block number
        int endBlock = endIdx / stopIdx;
        if (startBlock == endBlock || startBlock == maxFileCtr) {
            // This is likely going to be the case. If not, start is likely in
            //  the block right before the end index. It would be rare to need
            //  all of multiple blocks in between.
            // The second condition covers the case where both start and end of
            //  the selection are in the newest data section, but would be
            //  broken into different blocks later
            // In this case, we just copy out data in the buffer range
            startPos = Math.max(startPos,
                    startPos + stopIdx * (startBlock - maxFileCtr));
            System.arraycopy(buffer, startPos, result, 0, result.length);
        }

        else {
            // We have multiple blocks to read from
            // Copy data from the first block first
            int resultPos = stopIdx - startPos;
            System.arraycopy(buffer, startPos, result, 0, resultPos);

            // Get any in-between blocks
            int lastBlock = Math.min(endBlock, maxFileCtr);
            for (int i = startBlock + 1; i < lastBlock; i++) {
                loadBlock(DIR_NAME_BASE + i + ".bin");
                System.arraycopy(buffer, 0, result, resultPos, stopIdx);
                resultPos += stopIdx;
            }

            // Get remaining data
            if (endBlock >= maxFileCtr) {
                // If endBlock is gte maxFileCtr, get current data from block x
                // Must be gte because buffer of last items may be bigger than
                //  other blocks, so may not divide so cleanly into block #s
                loadBlock(DIR_NAME_BASE + "x.bin");
                currentOut = false;
            } else {
                // Load another regular block
                loadBlock(DIR_NAME_BASE + endBlock + ".bin");
            }
            System.arraycopy(buffer, 0, result, resultPos, result.length - resultPos);
        }

        return result;
    }

    /**
     * Return actual size of buffer (combination of the number of elements in
     * memory and on disk).
     * @return The size of the buffer.
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Close and clean up the buffer, removing all items paged to disk.
     */
    @Override
    public void close() {
        new File(DIR_NAME_BASE).getParentFile().listFiles(f -> {
            if (f.getName().endsWith(".bin")) f.delete();
            return true;
        });
    }

    /**
     * Dumps oldest data out of memory and onto disk.
     */
    private void dumpData() {
        // Dump the data in the buffer to the file
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(
          DIR_NAME_BASE + fileCtr++ + ".bin"))) {
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

    /**
     * Writes current buffer of data to disk. This is necessary only when an
     * older block of data is loaded into memory to be read. The purpose of this
     * method is to save off the newest data so it can be recovered.
     */
    private void writeInMemData() {
        try (DataOutputStream dos = new DataOutputStream(
          new FileOutputStream(DIR_NAME_BASE + "x.bin"))) {
            // Write out anything in the buffer
            for (int i = 0; i < maxHeadPtr; i++) {
                dos.writeDouble(buffer[i]);
            }
        } catch (IOException exc) {
            System.err.println("Failed to dump data!");
        }
    }

    /**
     * Load a data block back into memory.
     * @param blockName Name of (path to) the data block to retrieve.
     */
    private void loadBlock(String blockName) {
        // Evict current data if needed
        if (!currentOut && !blockName.endsWith("x.bin")) {
            currentOut = true;
            writeInMemData();
        }

        // Load in requested block
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

}
