import de.niklasfi.aocr.APdfRun;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        final var inputFilePath = args[0];
        final var outputFilePath = args[1];

        final var run = new APdfRun(inputFilePath, outputFilePath,
                APdfRun.ImageExtractionStrategy.EXTRACT_LARGEST_IMAGE_FROM_PAGE);

        final var bytes = run.run().blockingGet();
        try (final var os = new FileOutputStream(outputFilePath)) {
            os.write(bytes);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not open output stream for '%s'".formatted(outputFilePath), e);
        } catch (IOException e) {
            throw new RuntimeException("IOException when writing output file", e);
        }
    }
}
