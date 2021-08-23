package ru.vitenberg.fileupload;

import io.javalin.Javalin;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

public class FileUploadTest {
    private Javalin app = FileUpload.getApp();
    int appPort = 8989;
    private String uploadPath = "testupload";
    private String uploadField = "testfiles";
    private String url = String.format("http://localhost:%d/%s", appPort, uploadPath);
    private long uploadMaxSize = 50 * 1024 * 1024;

    @Test
    public void POST_nonempty_single_file() {
        app.post(uploadPath, FileUpload.postHandler(uploadField, uploadMaxSize));
        app.start(appPort);
        String fileContent = "Hello, world!";
        String fileName = "testFile";
        InputStream file = new ByteArrayInputStream(fileContent.getBytes());
        HttpResponse response = Unirest.post(url)
                .field(uploadField, file, fileName)
                .asEmpty();
        assertThat(response.getStatus()).isEqualTo(201);
        app.stop();
    }

    @Test
    public void POST_empty_single_file() {
        app.post(uploadPath, FileUpload.postHandler(uploadField, uploadMaxSize));
        app.start(appPort);
        String fileContent = "";
        String fileName = "emptyTestFile";
        InputStream file = new ByteArrayInputStream(fileContent.getBytes());
        HttpResponse response = Unirest.post(url)
                .field(uploadField, file, fileName)
                .asEmpty();
        assertThat(response.getStatus()).isEqualTo(201);
        app.stop();
    }

    @Test
    public void POST_non_multipart_data() {
        app.post(uploadPath, FileUpload.postHandler(uploadField, uploadMaxSize));
        app.start(appPort);
        HttpResponse response = Unirest.post(url)
                .body("")
                .asEmpty();
        assertThat(response.getStatus()).isEqualTo(500);
        app.stop();
    }

    @Test
    public void POST_large_file() {
        app.post(uploadPath, FileUpload.postHandler(uploadField, -2));
        app.start(appPort);
        String fileContent = "Hello";
        String fileName = "largeTestFile";
        InputStream file = new ByteArrayInputStream(fileContent.getBytes());
        HttpResponse response = Unirest.post(url)
                .field(uploadField, file, fileName)
                .asEmpty();
        assertThat(response.getStatus()).isEqualTo(400);
        app.stop();
    }

    @Test
    public void POST_file_wrong_field() {
        app.post(uploadPath, FileUpload.postHandler(uploadField, uploadMaxSize));
        app.start(appPort);
        String fileContent = "Hello";
        String fileName = "largeTestFile";
        String wrongField = "wrongfield";
        InputStream file = new ByteArrayInputStream(fileContent.getBytes());
        HttpResponse response = Unirest.post(url)
                .field(wrongField, file, fileName)
                .asEmpty();
        assertThat(response.getStatus()).isEqualTo(400);
        app.stop();
    }
}