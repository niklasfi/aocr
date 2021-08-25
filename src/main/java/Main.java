import de.niklasfi.aocr.APdfRun;
import de.niklasfi.aocr.FileUtil;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.internal.schedulers.ComputationScheduler;

public class Main {
    public static void main(String[] args) {
        final var inputFilePath = args[0];
        final var outputFilePath = args[1];

        final var run = new APdfRun(APdfRun.ImageExtractionStrategy.EXTRACT_LARGEST_IMAGE_FROM_PAGE);
        final var fileUtil = new FileUtil();

        final var scheduler = new ComputationScheduler();

        Flowable.just(inputFilePath)
                .subscribeOn(scheduler)
                .observeOn(scheduler)
                .flatMapSingle(fileUtil::readFile)
                .flatMapSingle(run::run)
                .flatMapCompletable(bytes -> fileUtil.writeFile(outputFilePath, bytes))
                .blockingAwait();
    }
}
