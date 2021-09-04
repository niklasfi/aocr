package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.io.*;

public class FileUtil {
    public Single<byte[]> readFile(String path) {
        if (path.equals("--")) {
            return Single.fromCallable(() -> readStreamInternal(System.in));
        }
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

    private byte[] readStreamInternal(InputStream stream) {
        final var is = new BufferedInputStream(stream);
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("could not read from input stream", e);
        }
    }

    public Completable writeFile(String path, byte[] data) {
        if (path.equals("--")) {
            return Completable.fromRunnable(() -> writeStreamInternal(System.out, data));
        }
        return Completable.fromRunnable(() -> writeFileInternal(path, data));
    }

    private void writeStreamInternal(OutputStream os, byte[] data) {
        try {
            os.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Could not write to output stream", e);
        }
    }

    private void writeFileInternal(String path, byte[] data) {
        try (final var os = new FileOutputStream(path)) {
            os.write(data);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not open output stream for '%s'".formatted(path), e);
        } catch (IOException e) {
            throw new RuntimeException("IOException when writing output file", e);
        }
    }
}
