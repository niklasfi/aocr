import de.niklasfi.aocr.*;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.internal.schedulers.ComputationScheduler;
import org.apache.commons.cli.*;
import org.apache.pdfbox.pdmodel.PDDocument;

public class Main {
    public static void main(String[] args) {
        final var options = new Options();

        final var endpointOption = new Option("e", "endpoint", true, "azure cognitive services endpoint url");
        endpointOption.setRequired(true);
        options.addOption(endpointOption);

        final var keyOption = new Option("k", "key", true, "subscription key to access azure cognitive services");
        keyOption.setRequired(true);
        options.addOption(keyOption);

        final var inputOption = new Option("i", "input", true, "path to input pdf file");
        inputOption.setRequired(true);
        options.addOption(inputOption);

        final var outputOption = new Option("o", "output", true, "path to save output to");
        outputOption.setRequired(true);
        options.addOption(outputOption);

        final CommandLineParser parser = new DefaultParser();
        final HelpFormatter formatter = new HelpFormatter();
        final CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("aocr", options);

            System.exit(1);
            return;
        }

        final var inputFilePath = cmd.getOptionValue("input");
        final var outputFilePath = cmd.getOptionValue("output");
        final var azureEndpoint = cmd.getOptionValue("endpoint");
        final var azureSubscriptionKey = cmd.getOptionValue("key");

        final var run = new AzurePdfAnnotator(azureEndpoint, azureSubscriptionKey);
        final var fileUtil = new FileUtil();
        final var pdfUtil = new PdfIoUtil();
        final PdfImageGetter pdfImageGetter = new PdfImageExtractor();

        final var scheduler = new ComputationScheduler();

        Flowable.just(inputFilePath)
                .subscribeOn(scheduler)
                .observeOn(scheduler)
                .flatMapSingle(fileUtil::readFile)
                .map(pdfUtil::readPdf)
                .flatMap(pdfImageGetter::getImages)
                .concatMapSingle(run::getAnnotationsForImage)
                .collect(PDDocument::new, run::addPageToDocument)
                .map(pdfUtil::savePdf)
                .flatMapCompletable(bytes -> fileUtil.writeFile(outputFilePath, bytes))
                .blockingAwait();
    }
}
