# findyr-tus-java-client

> **tus** is a protocol based on HTTP for *resumable file uploads*. Resumable
> means that an upload can be interrupted at any moment and can be resumed without
> re-uploading the previous data again. An interruption may happen willingly, if
> the user wants to pause, or by accident in case of an network issue or server
> outage.

**findyr-tus-java-client** is a library for uploading files using the *tus* protocol to any remote 
server supporting it. It is designed to be adaptable with third party HTTP clients, such as 
[Apache HTTP Client](https://hc.apache.org/httpcomponents-client-ga/) or [OkHttp](http://square.github.io/okhttp/).
This client is a fork of the [tus-java-client](https://github.com/tus/tus-java-client).

This library is also compatible with the Android platform and can be used without any modifications using the API. The [tus-android-client](https://github.com/tus/tus-android-client) provides additional classes which can be used in addition the Java library.

## Usage

```java
// Create a new TusClient instance
TusClient client = new TusClient();

// Configure tus HTTP endpoint. This URL will be used for creating new uploads
// using the Creation extension
client.setUploadCreationURL(new URL("http://master.tus.io/files"));

// Enable resumable uploads by storing the upload URL in memory
client.enableResuming(new TusURLMemoryStore());

// Open a file using which we will then create a TusUpload. If you do not have
// a File object, you can manually construct a TusUpload using an InputStream.
// See the documentation for more information.
File file = new File("./cute_kitten.png");
TusUpload upload = new TusUpload(file);

// First try to resume an upload. If that's not possible we will create a new
// upload and get a TusUploader in return. This class is responsible for opening
// a connection to the remote server and doing the uploading.
TusUploader uploader = client.resumeOrCreateUpload(upload);

// Upload the file in chunks of 1KB as long as data is available. Once the
// file has been fully uploaded the method will return -1
while(uploader.uploadChunk(1024 * 1024) < upload.getSize()) {
  // Calculate the progress using the total size of the uploading file and
  // the current offset.
  long totalBytes = upload.getSize();
  long bytesUploaded = uploader.getOffset();
  double progress = (double) bytesUploaded / totalBytes * 100;
}

// Allow the HTTP connection to be closed and cleaned up
uploader.close();

```

## Installation

Currently installation is only available by compiling from source.

## Documentation

Coming soon!

## License

MIT
