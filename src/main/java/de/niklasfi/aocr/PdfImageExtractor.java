package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;

public class PdfImageExtractor implements PdfImageRetriever {

    private Flowable<PDImageXObject> getImagesFromResources(PDResources resources) {
        return Flowable.fromIterable(resources.getXObjectNames())
                .map(resources::getXObject)
                .publish(xObjects -> Flowable.merge(
                        xObjects.ofType(PDFormXObject.class)
                                .map(PDFormXObject::getResources)
                                .flatMap(this::getImagesFromResources),
                        xObjects.ofType(PDImageXObject.class)
                                .map(xObject -> xObject)
                ))
                .reduce((single, max) -> {
                    if (single.getWidth() * single.getHeight() > max.getWidth() * max.getHeight()) {
                        return single;
                    }
                    return max;
                })
                .toFlowable();
    }

    @Override
    public Flowable<BufferedImage> getImages(PDDocument document) {
        return Flowable
                .range(0, document.getNumberOfPages())
                .map(document::getPage)
                .concatMapSingle(this::extractLargestImageFromPage);
    }

    private Single<BufferedImage> extractLargestImageFromPage(PDPage page) {
        return Flowable.just(page)
                .map(PDPage::getResources)
                .flatMap(this::getImagesFromResources)
                .firstOrError()
                .map(PDImageXObject::getImage);
    }

}
