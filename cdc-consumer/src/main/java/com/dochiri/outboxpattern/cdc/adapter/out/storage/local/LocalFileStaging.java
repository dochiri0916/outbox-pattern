package com.dochiri.outboxpattern.cdc.adapter.out.storage.local;

import com.dochiri.outboxpattern.application.storage.port.out.FileStagingPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStaging implements FileStagingPort {

    private final Path stagingDir;

    public LocalFileStaging(LocalFileStagingProperties properties) throws IOException {
        this.stagingDir = Path.of(properties.dir());
        Files.createDirectories(this.stagingDir);
    }

    public String stage(InputStream inputStream, String originalFileName) {
        String stagedFileName = UUID.randomUUID() + "_" + originalFileName;
        Path stagedPath = stagingDir.resolve(stagedFileName);
        try (OutputStream outputStream = Files.newOutputStream(stagedPath)) {
            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stage file: " + stagedFileName, e);
        }
        return stagedPath.toString();
    }

    public InputStream read(String path) {
        try {
            return Files.newInputStream(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read staged file: " + path, e);
        }
    }

    public void delete(String path) {
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException ignored) {
        }
    }

}
