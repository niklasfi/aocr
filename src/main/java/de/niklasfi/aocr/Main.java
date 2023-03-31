package de.niklasfi.aocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.niklasfi.aocr.azure.api.AzureApiAdapter;
import de.niklasfi.aocr.azure.api.AzureUriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.rendering.ImageType;

import java.util.Optional;

@Slf4j
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

        final var extractOption = new Option("r", "retrieve-method", true, """
                method to use to retrieve images from input pdf. Possible values:
                - extract (default): use the largest image on the page (useful for scans)
                - render: render the page into an image. dpi and color modes may be configured using --render-dpi and --render-color
                """);
        options.addOption(extractOption);

        final var renderDpiOption = new Option("d", "render-dpi", true, "dpi to use when rendering page from input pdf into an image. Defaults to 300 dpi.");
        options.addOption(renderDpiOption);

        final var renderColorOption = new Option("c", "render-color", true, """
                color scheme to use when rendering page from input pdf into an image. Possible values:
                - binary: convert to black / white image
                - gray: convert to grayscale image
                - rgb (default): convert to full color image
                """);
        options.addOption(renderColorOption);

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

        final var apiAdapter = new AzureApiAdapter(
                new AzureUriBuilder(azureEndpoint),
                azureSubscriptionKey,
                HttpClients.createDefault(),
                JsonMapper.builder().addModule(new JavaTimeModule()).build()
        );

        final var renderColor = switch (cmd.getOptionValue("render-color")) {
            case "binary" -> ImageType.BINARY;
            case "gray" -> ImageType.GRAY;
            case null, "rgb" -> ImageType.RGB;
            default -> throw new RuntimeException("could not parse render-color option");
        };

        final var renderDpi = Optional.ofNullable(cmd.getOptionValue("render-dpi")).map(Integer::parseInt).orElse(300);

        final var pdfImageRetriever = switch (cmd.getOptionValue("retrieve-method")) {
            case "extract", null -> new PdfImageExtractor();
            case "render" -> new PdfImageRenderer(renderDpi, renderColor);
            default -> throw new RuntimeException("could not parse render-method option");
        };

        final var fileUtil = new FileUtil();

        final var azurePdfOcr = new AzurePdfOcr(
                apiAdapter,
                pdfImageRetriever,
                fileUtil,
                (doc) -> PDType1Font.HELVETICA
        );
        azurePdfOcr.ocr(inputFilePath, outputFilePath);

        log.trace("goodbye from main");
        System.exit(0);
    }
}
