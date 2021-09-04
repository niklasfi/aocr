package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {
    public Single<byte[]> readFile(String path) {
        return Single.fromCallable(() -> readFileInternal(path));
    }

    private byte[] readFileInternal(String path) {
        final FileInputStream fis;
        try {
            fis = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not open input file", e);
        }

        final byte[] inputBytes;
        try {
            inputBytes = fis.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("could not read input file", e);
        }

        return inputBytes;
    }

    public Completable writeFile(String path, byte[] data) {
        return Completable.fromRunnable(() -> {
            try (final var os = new FileOutputStream(path)) {
                os.write(data);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("could not open output stream for '%s'".formatted(path), e);
            } catch (IOException e) {
                throw new RuntimeException("IOException when writing output file", e);
            }
        });
    }
}
