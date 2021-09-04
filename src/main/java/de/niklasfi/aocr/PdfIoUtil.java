package de.niklasfi.aocr;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PdfIoUtil {
    public byte[] savePdf(PDDocument pdDocument) {
        final var os = new ByteArrayOutputStream();
        try {
            pdDocument.save(os);
        } catch (IOException e) {
            throw new RuntimeException("could not save pdDocument to ByteArrayOutputStream", e);
        }
        try {
            pdDocument.close();
        } catch (IOException e) {
            throw new RuntimeException("could not close pdDocument after saving to ByteArrayOutputStream", e);
        }
        return os.toByteArray();
    }

    public PDDocument readPdf(byte[] pdfData) {
        try {
            return PDDocument.load(pdfData);
        } catch (IOException e) {
            throw new RuntimeException("could not read pdf", e);
        }
    }

}
