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
    private Optional<PDImageXObject> getImagesFromResources(PDResources resources) {
        // retrieve xObjects from resources
        final var xObjects = StreamSupport.stream(resources.getXObjectNames().spliterator(), false).map(cosName -> {
            try {
                return resources.getXObject(cosName);
            } catch (IOException e) {
                throw new RuntimeException("could not extract XObject", e);
            }
        });

        // recurse until only xImages are left
        final var xImages = xObjects.map(xObject -> switch (xObject) {
            case PDFormXObject xForm -> getImagesFromResources(xForm.getResources()).orElse(null);
            case PDImageXObject xImage -> xImage;
            default -> throw new IllegalStateException("Unexpected value: " + xObject);
        }).filter(Objects::nonNull);

        // return largest found image
        return xImages.max(Comparator.comparing(img -> img.getWidth() * img.getHeight()));
    }

    @Override
    public Stream<Optional<BufferedImage>> getImages(PDDocument document) {
        return IntStream.range(0, document.getNumberOfPages()).mapToObj(document::getPage).map(this::extractLargestImageFromPage);
    }

    private Optional<BufferedImage> extractLargestImageFromPage(PDPage page) {
        return this.getImagesFromResources(page.getResources()).map(xImage -> {
            try {
                return xImage.getImage();
            } catch (IOException e) {
                throw new RuntimeException("could not extract image from PDImageXObject", e);
            }
        });
    }

}
