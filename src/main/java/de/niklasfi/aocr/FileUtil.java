package de.niklasfi.aocr;

import java.io.*;

public class FileUtil {
    public byte[] readFile(String path) {
        if (path.equals("-")) {
            return readStream(System.in);
        }

        try (final FileInputStream fis = new FileInputStream(path)) {
            try {
                return fis.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException("could not read input file", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("could not open input file", e);
        }
    }

    public byte[] readStream(InputStream stream) {
        try (final var is = new BufferedInputStream(stream)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("could not read from input stream", e);
        }
    }

    public void writeFile(String path, byte[] data) {
        if (path.equals("--")) {
            writeStream(System.out, data);
            return;
        }

        try (final var os = new FileOutputStream(path)) {
            os.write(data);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not open output stream for '%s'".formatted(path), e);
        } catch (IOException e) {
            throw new RuntimeException("IOException when writing output file", e);
        }
    }

    public void writeStream(OutputStream os, byte[] data) {
        try {
            os.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Could not write to output stream", e);
        }
    }
}
