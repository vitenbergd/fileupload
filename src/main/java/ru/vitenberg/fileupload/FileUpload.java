package ru.vitenberg.fileupload;

import io.javalin.Javalin;
import io.javalin.core.util.FileUtil;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Handler;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;


@Command(name = "fileupload", mixinStandardHelpOptions = true, version = "0.0.1",
        description = "Uploads file to temporary directory via POST request")
class FileUpload implements Callable<Integer> {

    private static final String APP_PORT_ENV_VAR = "FU_APP_PORT";
    private static final String UPLOAD_PATH_ENV_VAR = "FU_UPLOAD_PATH";
    private static final String UPLOAD_FIELD_NAME_ENV_VAR = "FU_UPLOAD_PARAM_NAME";
    private static final String UPLOAD_MAX_FILE_SIZE_ENV_VAR = "FU_UPLOAD_MAX_FILE_SIZE";

    public static final String APP_PORT_DEFAULT = "8080";
    public static final String UPLOAD_PATH_DEFAULT = "upload";
    public static final String UPLOAD_FIELD_NAME_DEFAULT = "files";
    public static final int UPLOAD_MAX_FILE_SIZE_DEFAULT = 20 * 1024 * 1024;
    public static final String OUT_FILE_PREFIX = "fileupload-";
    public static final String OUT_FILE_SUFFIX = ".tmp";

    private static final String UPLOAD_PATH_REGEX = "^[a-z]{1,40}$";
    private static final String UPLOAD_FIELD_NAME_REGEX = "^[a-z]{1,40}$";

    @Spec
    CommandLine.Model.CommandSpec spec;

    public static int appPort;
    private static final String appPortDefault = "${env:"
            + APP_PORT_ENV_VAR
            + ":-"
            + APP_PORT_DEFAULT
            + "}";
    private static final String appPortDesc = "HTTP port to bind\n(default: '"
            + APP_PORT_DEFAULT
            + "', ENV var: "
            + APP_PORT_ENV_VAR
            + ")";

    @Option(names = {"-p", "--http-port"}, defaultValue = appPortDefault, description = appPortDesc)
    public void setAppPort(String value) {
        try {
            appPort = Integer.parseInt(value);
            if (appPort < 1024) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        String.format("Invalid value '%s' for option '--http-port'(%s): " +
                                "value should be in range [1024, 65535]", value, APP_PORT_ENV_VAR));
            }
        } catch (NumberFormatException nfe) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Invalid value '%s' for option '--http-port(%s)': " +
                            "value should be integer in range [1024, 65535]", value, APP_PORT_ENV_VAR));
        }
    }


    public static String uploadPath;
    private static final String uploadPathDefault = "${env:"
            + UPLOAD_PATH_ENV_VAR
            + ":-"
            + UPLOAD_PATH_DEFAULT
            + "}";
    private static final String uploadPathDesc = "POST request handler path\n(default: '"
            + UPLOAD_PATH_DEFAULT
            + "', ENV var: "
            + UPLOAD_PATH_ENV_VAR
            + ")";

    @Option(names = {"-u", "--upload-path"}, defaultValue = uploadPathDefault, description = uploadPathDesc)
    public void setUploadPath(String value) {
        if (!value.matches(UPLOAD_PATH_REGEX)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Invalid value '%s' for option '--upload-path'(%s): " +
                            "value should match '%s'", value, UPLOAD_PATH_ENV_VAR, UPLOAD_PATH_REGEX));
        }
        uploadPath = value;
    }

    public static String uploadFieldName;
    private static final String uploadFieldNameDefault = "${env:"
            + UPLOAD_FIELD_NAME_ENV_VAR
            + ":-"
            + UPLOAD_FIELD_NAME_DEFAULT
            + "}";
    private static final String uploadFieldNameDesc = "Upload request file field name\n(default: '"
            + UPLOAD_FIELD_NAME_DEFAULT
            + "', ENV var: "
            + UPLOAD_FIELD_NAME_ENV_VAR
            + ")";

    @Option(names = {"-f", "--field-name"}, defaultValue = uploadFieldNameDefault, description = uploadFieldNameDesc)
    public void setUploadFieldName(String value) {
        if (!value.matches(UPLOAD_FIELD_NAME_REGEX)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Invalid value '%s' for option '--field-name'(%s): " +
                            "value should match '%s'", value, UPLOAD_FIELD_NAME_ENV_VAR, UPLOAD_FIELD_NAME_REGEX));
        }
        uploadFieldName = value;
    }

    private static final String uploadMaxFileSizeDefault = "${env:"
            + UPLOAD_MAX_FILE_SIZE_ENV_VAR
            + ":-"
            + UPLOAD_MAX_FILE_SIZE_DEFAULT
            + "}";
    private static final String uploadMaxFileSizeDesc = "Upload request max size in bytes\n(default: '"
            + UPLOAD_MAX_FILE_SIZE_DEFAULT
            + "', ENV var: "
            + UPLOAD_MAX_FILE_SIZE_ENV_VAR
            + ")";
    @Option(names = {"-m", "--max-size"}, defaultValue = uploadMaxFileSizeDefault, description = uploadMaxFileSizeDesc)
    public static Long uploadMaxFileSize;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUpload.class);


    public static Javalin getApp() {
        Javalin app = Javalin.create(config -> {
            config.requestLogger((ctx, ms) -> {
                LOGGER.info("{} {} {} {}", ctx.ip(), ctx.method(), ctx.path(), ms);
            });
        });
        return app;
    }

    public Integer call() {
        Javalin app = getApp();
        app.post(uploadPath, postHandler(uploadFieldName, uploadMaxFileSize));
        app.start(appPort);
        LOGGER.info("started: port - '{}', upload path - '{}', field name - '{}', max file size - '{}'",
                appPort,
                uploadPath,
                uploadFieldName,
                uploadMaxFileSize);
        return 0;
    }

    public static void main(String[] args) {
        new CommandLine(new FileUpload()).execute(args);
    }

    private static Path createOutFile() throws java.io.IOException {
        return Files.createTempFile(OUT_FILE_PREFIX, OUT_FILE_SUFFIX);
    }

    public static Handler postHandler(String uploadFieldName, long uploadMaxFileSize) {
        return ctx -> {
            int contentLength = ctx.contentLength();
            LOGGER.info("Request 'Content-length' header value: {}", contentLength);
            if (contentLength > uploadMaxFileSize) {
                String msg = String.format("Request size is to big '%d' (max size: '%d')", contentLength, uploadMaxFileSize);
                LOGGER.error(msg);
                throw new BadRequestResponse(msg);
            }
            if (!ctx.isMultipartFormData()) {
                String msg = "Invalid request content type, should be 'multipart/form data'";
                LOGGER.error(msg);
                throw new InternalServerErrorResponse(msg);
            }
            List<UploadedFile> files;
            try {
                files = ctx.uploadedFiles(uploadFieldName);
            } catch (Exception e) {
                String msg = "Malformed request";
                LOGGER.error("{}:", msg, e);
                throw new InternalServerErrorResponse(msg);
            }
            if (files.isEmpty()) {
                String msg = String.format("No files found in '%s' field", uploadFieldName);
                LOGGER.error(msg);
                throw new BadRequestResponse(msg);
            }
            LOGGER.info("Total files in '{}' field : {}", uploadFieldName, files.size());
            ArrayList<HashMap<String, String>> response = new ArrayList<>();
            files.forEach(file -> {
                String fileName = file.getFilename();
                long fileSize = file.getSize();
                LOGGER.info("Processing file: {} (size: {})", fileName, fileSize);
                if (fileSize > 0) {
                    try {
                        Path outFile = createOutFile();
                        FileUtil.streamToFile(file.getContent(), outFile.toString());
                        LOGGER.info("File '{}' is written to: {}", fileName, outFile);
                        HashMap<String, String> responseEntity = new HashMap<>();
                        responseEntity.put("originalFileName", fileName);
                        responseEntity.put("outFilePath", outFile.toString());
                        responseEntity.put("uploadFieldName", uploadFieldName);
                        response.add(responseEntity);
                    } catch (Exception e) {
                        String msg = String.format("Error while processing '%s' file", fileName);
                        LOGGER.error("{}:", msg, e);
                        throw new InternalServerErrorResponse(msg);
                    }
                } else {
                    LOGGER.info("Uploaded file '{}' is empty, skipping", fileName);
                }
            });
            ctx.status(201);
            ctx.json(response);
        };
    }
}