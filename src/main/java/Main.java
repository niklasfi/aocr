import de.niklasfi.aocr.*;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.internal.schedulers.ComputationScheduler;
import org.apache.pdfbox.pdmodel.PDDocument;

public class Main {
    public static void main(String[] args) {
        final var inputFilePath = args[0];
        final var outputFilePath = args[1];

        final var azureEndpoint = "";
        final var azureSubscriptionKey = "";

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
