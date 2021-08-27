package de.niklasfi.aocr;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

    // https://docs.microsoft.com/en-us/azure/cognitive-services/computer-vision/vision-api-how-to-topics/call-read-api
    // free tier: 20 calls per minute
    public final static TemporalAmount LIMIT_INTERVAL_FREE_TIER = Duration.of(60000 / 20, ChronoUnit.MILLIS);

    // paid tier: 10 calls per second
    public final static TemporalAmount LIMIT_INTERVAL_PAID_TIER = Duration.of(1000 / 10, ChronoUnit.MILLIS);

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

        final var paidOption = new Option("p", "paid", false, "azure subscription is on paid tier");
        options.addOption(paidOption);

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

        final var pdfImageRetriever = new PdfImageExtractor();
        final var pdfIoUtil = new PdfIoUtil();
        final var fileUtil = new FileUtil();
        final var throttlerInterval = cmd.hasOption("paid") ? LIMIT_INTERVAL_PAID_TIER : LIMIT_INTERVAL_FREE_TIER;

        final var azurePdfOcr =
                new AzurePdfOcr(azureEndpoint, azureSubscriptionKey, pdfImageRetriever, pdfIoUtil, fileUtil,
                                throttlerInterval);
        azurePdfOcr.ocrSync(inputFilePath, outputFilePath);
    }
}
