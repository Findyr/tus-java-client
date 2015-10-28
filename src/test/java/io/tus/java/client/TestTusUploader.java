package io.tus.java.client;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

public class TestTusUploader {
    private MockServerClient mockServer;
    public URL mockServerURL;

    @Before
    public void setUp() throws Exception {
        int port = PortFactory.findFreePort();
        mockServerURL = new URL("http://localhost:" + port + "/files");
        mockServer = startClientAndServer(port);
    }

    @After
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testTusUploader() throws IOException, ProtocolException {
        byte[] content = "hello world".getBytes();
        ByteArrayInputStream bis = new ByteArrayInputStream(content);

        mockServer.when(new HttpRequest()
                        .withPath("/files/foo")
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "3")
                        .withBody(Arrays.copyOfRange(content, 3, 8))
        ).respond(new HttpResponse()
                        .withStatusCode(204)
                        .withHeader("Tus-Resumable", TusClient.TUS_VERSION)
                        .withHeader("Upload-Offset", "8"));

        TusClient client = new TusClient();
        URL uploadUrl = new URL(mockServerURL + "/foo");
        TusUpload upload = new TusUpload();
        upload.setInputStream(bis);
        long offset = 3;

        TusUploader uploader = new TusUploader(client, uploadUrl, upload, offset);
        assertEquals(8, uploader.uploadChunk(5));
    }
}
