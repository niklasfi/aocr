package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.internal.schedulers.ComputationScheduler;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

public class AzurePdfOcr {
    private final String azureEndpoint;
    private final String azureSubscriptionKey;

    private final PdfImageRetriever pdfImageRetriever;
    private final PdfIoUtil pdfUtil;
    private final FileUtil fileUtil;
    private final TemporalAmount throttlerInterval;

    // https://docs.microsoft.com/en-us/azure/cognitive-services/computer-vision/vision-api-how-to-topics/call-read-api
    // free tier: 20 calls per minute
    public final static TemporalAmount LIMIT_INTERVAL_FREE_TIER = Duration.of(60000 / 20, ChronoUnit.MILLIS);

    // paid tier: 10 calls per second
    public final static TemporalAmount LIMIT_INTERVAL_PAID_TIER = Duration.of(1000 / 10, ChronoUnit.MILLIS);

    public AzurePdfOcr(String azureEndpoint, String azureSubscriptionKey, PdfImageRetriever pdfImageRetriever, PdfIoUtil pdfUtil, FileUtil fileUtil,
                       TemporalAmount throttlerInterval) {
        this.azureEndpoint = azureEndpoint;
        this.azureSubscriptionKey = azureSubscriptionKey;
        this.pdfImageRetriever = pdfImageRetriever;
        this.pdfUtil = pdfUtil;
        this.fileUtil = fileUtil;
        this.throttlerInterval = throttlerInterval;
    }

    public Single<byte[]> ocr(byte[] inputPdf) {
        final var run = new AzurePdfAnnotator(azureEndpoint, azureSubscriptionKey, throttlerInterval);

        return Flowable.just(inputPdf)
                .map(pdfUtil::readPdf)
                .flatMap(pdfImageRetriever::getImages)
                .concatMapSingle(run::getAnnotationsForImage)
                .collect(PDDocument::new, run::addPageToDocument)
                .map(pdfUtil::savePdf);
    }

    public Single<InputStream> ocr(InputStream inputStream) {
        final var fileUtil = new FileUtil();

        return Single.just(inputStream)
                .map(fileUtil::readStream)
                .flatMap(this::ocr)
                .map(ByteArrayInputStream::new);
    }

    public Completable ocr(String inputFilePath, String outputFilePath) {
        return Single.just(inputFilePath)
                .map(fileUtil::readFile)
                .flatMap(this::ocr)
                .flatMapCompletable(pdfBytes -> Completable.fromRunnable(() -> fileUtil.writeFile(outputFilePath, pdfBytes)));
    }

    public void ocrSync(String inputFilePath, String outputFilePath) {
        final var scheduler = new ComputationScheduler();

        this.ocr(inputFilePath, outputFilePath)
                .subscribeOn(scheduler)
                .observeOn(scheduler)
                .blockingAwait();
    }
}
