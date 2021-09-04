import de.niklasfi.aocr.APdfRun;

public class Main {
    public static void main(String[] args) {
        final var inputFilePath = args[0];
        final var outputFilePath = args[1];

        final var run = new APdfRun(inputFilePath, outputFilePath,
                APdfRun.ImageExtractionStrategy.EXTRACT_LARGEST_IMAGE_FROM_PAGE);
        run.run();
    }
}
