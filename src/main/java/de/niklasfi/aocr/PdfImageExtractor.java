package de.niklasfi.aocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PdfImageExtractor implements PdfImageRetriever {
    private Optional<PDImageXObject> getImagesFromResources(PDResources resources, TracebackInfo trace) {
        // retrieve xObjects from resources
        final var xObjects = StreamSupport.stream(resources.getXObjectNames().spliterator(), false).map(cosName -> {
            try {
                return resources.getXObject(cosName);
            } catch (IOException e) {
                throw new RuntimeException("%s could not extract XObject".formatted(trace), e);
            }
        });

        // recurse until only xImages are left
        final var xImages = xObjects.map(xObject -> switch (xObject) {
            case PDFormXObject xForm -> getImagesFromResources(xForm.getResources(), trace).orElse(null);
            case PDImageXObject xImage -> xImage;
            default -> throw new IllegalStateException("%s Unexpected value: %s".formatted(trace, xObject));
        }).filter(Objects::nonNull);

        // return largest found image
        return xImages.max(Comparator.comparing(img -> img.getWidth() * img.getHeight()));
    }

    @Override
    public Stream<TracebackContainer<Optional<BufferedImage>>> getImages(PDDocument document, TracebackInfo trace) {
        return IntStream.range(0, document.getNumberOfPages())
                .mapToObj(pageIdx -> new TracebackContainer<>(new TracebackInfo(trace.job(), pageIdx), document.getPage(pageIdx)))
                .map(container -> extractLargestImageFromPage(container.data(), container.trace()));
    }

    private TracebackContainer<Optional<BufferedImage>> extractLargestImageFromPage(PDPage pageContainer, TracebackInfo trace) {
        return new TracebackContainer<>(trace, this.getImagesFromResources(pageContainer.getResources(), trace).map(xImage -> {
            try {
                return xImage.getImage();
            } catch (IOException e) {
                throw new RuntimeException("%s could not extract image from PDImageXObject".formatted(trace), e);
            }
        }));
    }

}
