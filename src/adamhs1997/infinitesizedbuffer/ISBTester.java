package adamhs1997.infinitesizedbuffer;

import java.util.Arrays;

public class ISBTester {

    // For testing...
    public static void main(String[] args) {
        try (InfiniteSizedBuffer isb = new InfiniteSizedBuffer(10, 30)) {
//            basicTests(isb);

            isb.writeMultiple(new double[] {1, 2, 3, 4, 5});
            isb.writeMultiple(new double[] {6, 7, 8, 9, 10, 11, 12, 13, 14, 15 ,16, 17, 18, 19, 20, 21, 22, 23});
            for (int i = 0; i < isb.getBufferSize(); i++) {
                System.out.println(isb.readData());
            }
            // Reads base idx -> end - 1
            System.out.println("end " + Arrays.toString(isb.readRange(3, 23)));

        }
    }

    private static void basicTests(InfiniteSizedBuffer isb) {
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
