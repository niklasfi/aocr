package de.niklasfi.aocr;

import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVision;
import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVisionManager;
import com.microsoft.azure.cognitiveservices.vision.computervision.implementation.ComputerVisionImpl;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.*;
import com.microsoft.rest.ServiceResponseWithHeaders;
import de.niklasfi.rx.Throttler;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleObserver;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class APdfRun {
    private final ImageExtractionStrategy imageExtractionStrategy;

    public enum ImageExtractionStrategy {
        RENDER_PAGE_AS_IMAGE,
        EXTRACT_LARGEST_IMAGE_FROM_PAGE
    }

    public APdfRun(ImageExtractionStrategy imageExtractionStrategy) {
        this.imageExtractionStrategy = imageExtractionStrategy;
    }

    public Single<byte[]> run(byte[] inputBytes) {
        return Flowable
                .just(inputBytes)
                .map(this::readPdf)
                .flatMap(this::getImagesFromPdf)
                .flatMap(new Throttler<>(Duration.of(6000, ChronoUnit.MILLIS)))
                .flatMapSingle(this::azureReadImage)
                .collect(PDDocument::new, this::addPageToDocument)
                .map(this::pdDocumentToBytes);
    }

    private byte[] pdDocumentToBytes(PDDocument pdDocument) {
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

    private PDDocument readPdf(byte[] pdfData) {
        try {
            return PDDocument.load(pdfData);
        } catch (IOException e) {
            throw new RuntimeException("could not read pdf", e);
        }
    }

    private Flowable<BufferedImage> renderEachPageAsImage(PDDocument document) {
        final var renderer = new PDFRenderer(document);

        return Flowable
                .range(0, document.getNumberOfPages())
                .map(pageIdx -> {
                    final BufferedImage bufferedImg;
                    try {
                        bufferedImg = renderer.renderImageWithDPI(pageIdx, 72, ImageType.RGB);
                    } catch (IOException e) {
                        throw new RuntimeException("could not render image of page %s".formatted(pageIdx + 1), e);
                    }
                    return bufferedImg;
                });
    }

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

    private Flowable<BufferedImage> extractLargestImageFromEachPage(PDDocument document) {
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

    private Flowable<BufferedImage> getImagesFromPdf(PDDocument document) {
        return switch (imageExtractionStrategy) {
            case RENDER_PAGE_AS_IMAGE -> renderEachPageAsImage(document);
            case EXTRACT_LARGEST_IMAGE_FROM_PAGE -> extractLargestImageFromEachPage(document);
        };
    }

    private byte[] bufferedImageToByteArray(BufferedImage bufferedImage) {
        final var os = new ByteArrayOutputStream();
        try {
            if (!ImageIOUtil.writeImage(bufferedImage, "png", os)) {
                throw new RuntimeException("could not render image from bufferedImage");
            }
        } catch (IOException e) {
            throw new RuntimeException("could not render image from bufferedImage", e);
        }
        return os.toByteArray();
    }

    private Single<AnnotatedImage> azureReadImage(BufferedImage bufferedImage) {
        final String subscriptionKey = "";
        final String endpoint = "";

        final var compVisClient = ComputerVisionManager.authenticate(subscriptionKey).withEndpoint(endpoint);
        final var compVisImpl = (ComputerVisionImpl) compVisClient.computerVision();

        return Single.just(bufferedImage)
                .map(this::bufferedImageToByteArray)
                .flatMap(imgBytes -> new Single<ServiceResponseWithHeaders<Void, ReadInStreamHeaders>>() {
                            @Override
                            protected void subscribeActual(@NonNull SingleObserver<? super ServiceResponseWithHeaders<Void, ReadInStreamHeaders>> observer) {
                                compVisImpl
                                        .readInStreamWithServiceResponseAsync(
                                                imgBytes,
                                                OcrDetectionLanguage.DE,
                                                null,
                                                null,
                                                null)
                                        .subscribe(new ForwardingSingleObserver<>(observer));
                            }
                        }
                )
                .map(response -> response.headers().operationLocation())
                .map(this::extractOperationIdFromOpLocation)
                .flatMap(operationId -> pollReadApiResult(compVisImpl, operationId))
                .map(readOperationResult -> new AnnotatedImage(bufferedImage, readOperationResult.analyzeResult()));
    }

    private Single<ReadOperationResult> pollReadApiResult(ComputerVision compVis, UUID operationId) {
        return Flowable.interval(0, 1000, TimeUnit.MILLISECONDS)
                .map(i -> compVis.getReadResult(operationId))
                .filter(Objects::nonNull)
                .filter(r -> r.status() == OperationStatusCodes.SUCCEEDED || r.status() == OperationStatusCodes.FAILED)
                .firstOrError();
    }

    private record AnnotatedImage(BufferedImage bufferedImage, AnalyzeResults analyzeResults) {

    }

    private void addPageToDocument(PDDocument pdDocument, BufferedImage bufferedImage) {
        final var pdPage = blankPageFromBufferedImage(pdDocument, bufferedImage);
        addBufferedImageToPage(pdDocument, pdPage, bufferedImage);
    }

    private void addPageToDocument(PDDocument pdDocument, AnnotatedImage annotatedImage) {
        final var pdPage = blankPageFromBufferedImage(pdDocument, annotatedImage.bufferedImage());
        addBufferedImageToPage(pdDocument, pdPage, annotatedImage.bufferedImage());
        addAnalyzeResultsToPage(pdDocument, pdPage, annotatedImage.analyzeResults());
    }

    private PDPage blankPageFromBufferedImage(PDDocument pdDocument, BufferedImage bufferedImage) {
        final var pdWidth = Math.round(bufferedImage.getWidth() * 72. / 72.);
        final var pdHeight = Math.round(bufferedImage.getHeight() * 72. / 72.);
        final var pageMediaBoxRect = new PDRectangle(pdWidth, pdHeight);
        final var pdPage = new PDPage(pageMediaBoxRect);
        pdDocument.addPage(pdPage);

        return pdPage;
    }

    private void addBufferedImageToPage(PDDocument pdDocument, PDPage pdPage, BufferedImage bufferedImage) {
        try (final var cs = new PDPageContentStream(pdDocument, pdPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
            final var pdImg = LosslessFactory.createFromImage(pdDocument, bufferedImage);
            cs.drawImage(pdImg, 0, 0, pdPage.getMediaBox().getWidth(), pdPage.getMediaBox().getHeight());
        } catch (IOException e) {
            throw new RuntimeException("could not create page content stream", e);
        }
    }

    private static final int BB_TL_X = 0;
    private static final int BB_TL_Y = 1;
    private static final int BB_TR_X = 2;
    private static final int BB_TR_Y = 3;
    private static final int BB_BR_X = 4;
    private static final int BB_BR_Y = 5;
    private static final int BB_BL_X = 6;
    private static final int BB_BL_Y = 7;
    private static final int FONT_SIZE = 12;

    private void addAnalyzeResultsToPage(PDDocument pdDocument, PDPage pdPage, AnalyzeResults analyzeResults) {
        final var font = PDType1Font.HELVETICA;

        // width and height of pdf page
        final var wp = pdPage.getMediaBox().getWidth();
        final var hp = pdPage.getMediaBox().getHeight();

        try (final var cs = new PDPageContentStream(pdDocument, pdPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.setFont(font, FONT_SIZE);

            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(0.f);
            cs.setGraphicsStateParameters(graphicsState);

            for (final var readResult : analyzeResults.readResults()) {
                // width and height of azure image
                final var wa = (float) readResult.width();
                final var ha = (float) readResult.height();

                // see https://en.wikipedia.org/wiki/Affine_transformation
                // use
                //
                //      ( wa, 0 , 1 )           ( wp, hp, 1 )
                // X := ( 0 , ha, 1 ) ,    Y := ( 0 , 0 , 1 )
                //      ( 0 , 0 , 1 )           ( 0 , hp, 1 )
                //
                // solve Y = X . M for M ===> (X^-1) * Y = M
                // M is the affine transformation matrix we are looking for

                final var azurePdfAffineTransform = new AffineTransform(
                        wp / wa, 0, // 0
                        0, -hp / ha, // 0
                        0, hp // 1
                );

                for (final var line : readResult.lines()) {
                    final var bb = line.boundingBox();

                    final var azurePoints = List.of(
                            new Point2D.Float(bb.get(BB_TL_X).floatValue(), bb.get(BB_TL_Y).floatValue()),
                            new Point2D.Float(bb.get(BB_TR_X).floatValue(), bb.get(BB_TR_Y).floatValue()),
                            new Point2D.Float(bb.get(BB_BR_X).floatValue(), bb.get(BB_BR_Y).floatValue()),
                            new Point2D.Float(bb.get(BB_BL_X).floatValue(), bb.get(BB_BL_Y).floatValue())
                    );

                    final var pdfPoints = azurePoints.stream()
                            .map(point -> (Point2D.Float) azurePdfAffineTransform.transform(point, null))
                            .collect(Collectors.toList());

                    // vector along the bottom horizontal of pdf coordinates
                    final var vh = new Point2D.Float(
                            pdfPoints.get(2).x - pdfPoints.get(3).x,
                            pdfPoints.get(2).y - pdfPoints.get(3).y
                    );

                    // vector along the left vertical of the pdf coordinates
                    final var vv = new Point2D.Float(
                            pdfPoints.get(0).x - pdfPoints.get(3).x,
                            pdfPoints.get(0).y - pdfPoints.get(3).y
                    );

                    // projection of vv onto vh
                    final var vvPrjFactor = (vh.x * vv.x + vh.y * vv.y) / (float) vh.distanceSq(0, 0);
                    final var vvPrj = new Point2D.Float(vh.x * vvPrjFactor, vh.y * vvPrjFactor);
                    // remainder after projection, a.k.a. height
                    final var targetHeight = (float) new Point2D.Float(vv.x - vvPrj.x, vv.y - vvPrj.y).distance(0, 0);

                    // projection of vh onto vv
                    final var vhPrjFactor = (vh.x * vv.x + vh.y * vv.y) / (float) vv.distanceSq(0, 0);
                    final var vhPrj = new Point2D.Float(vv.x * vhPrjFactor, vv.y * vhPrjFactor);
                    // remainder after projection, a.k.a. width
                    final var targetWidth = (float) new Point2D.Float(vh.x - vhPrj.x, vh.y - vhPrj.y).distance(0, 0);

                    // angle of vh with respect to the x-axis
                    final var angle = Math.atan2(vh.y, vh.x);

                    // bottom left-hand corner of pdf text box
                    final var offset = pdfPoints.get(3);

                    // https://stackoverflow.com/questions/13701017/calculation-string-width-in-pdfbox-seems-only-to-count-characters
                    // https://stackoverflow.com/questions/17171815/get-the-font-height-of-a-character-in-pdfbox
                    final var baseTextWidth = font.getStringWidth(line.text()) / 1000 * FONT_SIZE;
                    final var scaleX = targetWidth / baseTextWidth;

                    // https://stackoverflow.com/questions/17171815/get-the-font-height-of-a-character-in-pdfbox
                    final var baseTextScale = font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000;
                    final var baseTextHeight = baseTextScale * FONT_SIZE;
                    final var scaleY = targetHeight / baseTextHeight;
                    // move text up a bit to compensate for the fact that the bottom of the text box does not correspond
                    // with the baseline of the text
                    final var translateY = -(1 - baseTextScale) * FONT_SIZE * scaleY;

                    final var sizeScale = Matrix.getScaleInstance(scaleX, scaleY);
                    final var sizeTranslate = Matrix.getTranslateInstance(0, translateY);
                    final var originTransform = Matrix.getRotateInstance(angle, offset.x, offset.y);

                    // create total transform by combining those defined above
                    final var totalTransform = originTransform.clone();
                    totalTransform.concatenate(sizeTranslate);
                    totalTransform.concatenate(sizeScale);

                    // actually draw the line
                    cs.beginText();
                    cs.setTextMatrix(totalTransform);
                    cs.newLine();
                    cs.showText(line.text());
                    cs.endText();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("could not create page content stream", e);
        }
    }

    private record ForwardingSingleObserver<T>(
            SingleObserver<T> observer) implements rx.Observer<T> {

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            observer.onError(e);
        }

        @Override
        public void onNext(T data) {
            observer.onSuccess(data);
        }
    }

    private UUID extractOperationIdFromOpLocation(String operationLocation) {
        if (operationLocation != null && !operationLocation.isEmpty()) {
            String[] splits = operationLocation.split("/");

            if (splits.length > 0) {
                return UUID.fromString(splits[splits.length - 1]);
            }
        }
        throw new IllegalStateException("Something went wrong: Couldn't extract the operation id from the operation location");
    }
}
