package io.tus.java.client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

public class TestTusUploader {
    private MockServerClient mockServer;
    public URL mockServerURL;
    private byte[] content;

    @Before
    public void setUp() throws Exception {
        int port = PortFactory.findFreePort();
        mockServerURL = new URL("http://localhost:" + port + "/files");
        mockServer = startClientAndServer(port);
        content = "hello world".getBytes();
    }

    @After
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testTusUploader() throws IOException, ProtocolException {
        ByteArrayInputStream bis = new ByteArrayInputStream(content);

        mockServer.when(new HttpRequest()
                        .withPath("/files/foo")
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "3")
                        .withBody(Arrays.copyOfRange(content, 3, 8))
        ).respond(new HttpResponse()
                        .withStatusCode(204)
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "8")
        );
        mockServer.when(new HttpRequest()
                        .withPath("/files/foo")
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "8")
                        .withBody(Arrays.copyOfRange(content, 8, 11))
        ).respond(new HttpResponse()
                        .withStatusCode(204)
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "11")
        );

        TusClient client = new TusClient();
        URL uploadUrl = new URL(mockServerURL + "/foo");
        TusUpload upload = new TusUpload();
        upload.setInputStream(bis);
        long offset = 3;

        TusUploader uploader = new TusUploader(client, uploadUrl, upload, offset);
        assertEquals(8, uploader.uploadChunk(5));
        assertEquals(11, uploader.uploadChunk(5));
        upload.close();
    }

    @Test
    public void testUploadServerError() throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(content);

        mockServer.when(new HttpRequest()
                        .withPath("/files/foo")
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "3")
        ).respond(new HttpResponse().withStatusCode(502));
        TusClient client = new TusClient();
        URL uploadUrl = new URL(mockServerURL + "/foo");
        TusUpload upload = new TusUpload();
        upload.setInputStream(bis);
        long offset = 3;

        TusUploader uploader = new TusUploader(client, uploadUrl, upload, offset);
        try {
            uploader.uploadChunk(8);
            fail("Expected exception to be thrown!");
        } catch (ProtocolException e) {
            // Test that in the event of an exception, the input stream is re-set to the offset.
            assertEquals(502, e.getStatusCode());
            byte[] remainingContents = new byte[8];
            int bytesRead = upload.getInputStream().read(remainingContents);
            assertEquals(8, bytesRead);
            assertArrayEquals(Arrays.copyOfRange(content, 3, 11), remainingContents);
        }
        upload.close();
    }

    @Test
    public void testUploadIOError() throws IOException, ProtocolException {
        ByteArrayInputStream bis = new ByteArrayInputStream(content);
        HttpProvider mockProvider = mock(HttpProvider.class);
        TusClient client = new TusClient(mockProvider);
        URL uploadUrl = new URL(mockServerURL + "/foo");
        TusUpload upload = new TusUpload();
        upload.setInputStream(bis);
        upload.setSize(content.length);
        long offset = 3;
        try {
            when(mockProvider.getRequestBuilder(uploadUrl))
                    .thenReturn(mock(io.tus.java.client.HttpRequest.Builder.class));
            when(mockProvider.executeRequest(any(io.tus.java.client.HttpRequest.class)))
                    .thenThrow(new IOException("Test IO error!"));
            TusUploader uploader = new TusUploader(client, uploadUrl, upload, offset);
            uploader.uploadChunk(8);
            fail("Expected exception to be thrown!");
        } catch (IOException e) {
            // Test that in the event of an exception, the input stream is re-set to the offset.
            byte[] remainingContents = new byte[8];
            int bytesRead = upload.getInputStream().read(remainingContents);
            assertEquals(8, bytesRead);
            assertArrayEquals(Arrays.copyOfRange(content, 3, 11), remainingContents);
        }

    }

}
