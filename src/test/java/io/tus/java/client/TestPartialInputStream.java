package io.tus.java.client;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the <code>PartialInputStream</code>
 */
public class TestPartialInputStream {

    private byte[] testInput;

    @Before
    public void setUp() throws Exception {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        try (OutputStream out = new BufferedOutputStream(bytesOut)) {
            Random byteGen = new Random();
            for (int i = 0; i < 1024; i++) {
                out.write(byteGen.nextInt());
            }
            out.flush();
        }
        this.testInput = bytesOut.toByteArray();
        bytesOut.close();
    }

    @Test
    public void testPartialFileRead() {
        checkFileRead(10, 0);
        checkFileRead(100, 100);
        // Less than 100 bytes should be read.
        checkFileRead(100, 1000);
    }

    @Test
    public void testFullFileRead() {
        checkFileRead(0, 0);
    }

    public void checkFileRead(int bytesToRead, int bytesToSkip) {
        try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
             ByteArrayInputStream bytesIn = new ByteArrayInputStream(this.testInput);
             PartialInputStream subject = new PartialInputStream(bytesIn, bytesToRead);
             OutputStream out = new BufferedOutputStream(bytesOut)) {
            long skippedBytes = subject.skip(bytesToSkip);
            int data = 0;
            while (data > -1) {
                data = subject.read();
                if (data > -1) {
                    out.write(data);
                }
            }
            out.flush();
            byte[] output = bytesOut.toByteArray();
            int bytesRemaining = this.testInput.length - bytesToSkip;
            if (bytesToRead <= 0) {
                bytesToRead = this.testInput.length;
            }
            int expectedBytesRead = Math.min(bytesToRead, bytesRemaining);
            int expectedFinalIndex = bytesToSkip + expectedBytesRead;
            assertEquals(expectedBytesRead, output.length);
            byte[] expectedData = Arrays.copyOfRange(this.testInput, bytesToSkip,
                    expectedFinalIndex);
            assertArrayEquals(expectedData, output);
        } catch (FileNotFoundException e) {
            fail("Could not locate test file!");
        } catch (IOException e) {
            fail("IO Exception occurred: " + e.getMessage());
        }
    }

    @Test
    public void testCloseBehavior() throws IOException {
        BufferedInputStream inputStream =
                new BufferedInputStream(new ByteArrayInputStream(this.testInput));
        PartialInputStream subject = new PartialInputStream(inputStream, 0);
        try {
            subject.close();
        } catch (IOException e) {
            fail("Should not throw IOException when closing!");
        }

        try {
            byte[] output = new byte[this.testInput.length];
            int bytesRead = inputStream.read(output);
            assertEquals(bytesRead, this.testInput.length);
            assertArrayEquals(this.testInput, output);
        } catch (IOException e) {
            fail("Underlying input stream should not be closed!");
        } finally {
            inputStream.close();
        }
    }
}
