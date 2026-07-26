package com.dochiri.outboxpattern.application.storage.port.out;

import java.io.InputStream;

public interface FileStagingPort {

    String stage(InputStream inputStream, String originalFileName);

    InputStream read(String path);

    void delete(String path);
}
