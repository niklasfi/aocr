package de.niklasfi.aocr;

import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVisionManager;
import com.microsoft.azure.cognitiveservices.vision.computervision.implementation.ComputerVisionImpl;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ComputerVisionOcrErrorException;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.OcrDetectionLanguage;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.OperationStatusCodes;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ReadInStreamHeaders;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.ReadOperationResult;
import com.microsoft.rest.ServiceResponseWithHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class AzureApiHandler {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ComputerVisionImpl compVisClientImpl;

    public AzureApiHandler(String endpoint, String subscriptionKey) {
        final var compVisClient = ComputerVisionManager.authenticate(subscriptionKey).withEndpoint(endpoint);
        compVisClientImpl = (ComputerVisionImpl) compVisClient.computerVision();

    }

    /**
     * class contains code executed in worker thread.
     * non-static nested class, uses compVisClientImpl from outer class
     */
    private class AnnotationJob {
        /**
         * main method for worker thread, queries azure read api to retrieve annotations for passed image
         *
         * @param bufferedImage image to be annotated
         * @return image with annotations from azure read api. may be empty, if api operation fails
         */
        public Optional<AnnotatedImage> getAnnotationsForImage(BufferedImage bufferedImage) {
            final var imgBytes = bufferedImageToByteArray(bufferedImage);

            log.trace("creating read operation");
            UUID extractedOperationId = null;
            while (extractedOperationId == null) {
                try {
                    final var serviceResponse = compVisClientImpl.readInStreamWithServiceResponseAsync(imgBytes, OcrDetectionLanguage.DE,
                            null,
                            null,
                            "natural").toBlocking().first();

                    final var operationLocation = extractOperationLocationFromResponse(serviceResponse);
                    extractedOperationId = extractOperationIdFromOpLocation(operationLocation);
                } catch (ComputerVisionOcrErrorException e) {
                    handleComputerVisionOcrErrorException(e);
                }
            }
            log.trace("created read operation, operationId = '{}'", extractedOperationId);

            log.trace("extracting operation result");
            ReadOperationResult apiResult = null;
            while (apiResult == null) {
                try {
                    final var readResultTmp = compVisClientImpl.getReadResult(extractedOperationId);
                    if (readResultTmp.status() == OperationStatusCodes.SUCCEEDED || readResultTmp.status() == OperationStatusCodes.FAILED) {
                        apiResult = readResultTmp;
                        continue;
                    }

                } catch (ComputerVisionOcrErrorException e) {
                    handleComputerVisionOcrErrorException(e);
                }

                try {
                    Thread.sleep(Duration.ofSeconds(1).toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException("sleep was interrupted", e);
                }
            }
            log.info("extracted operation result. status is {}", apiResult.status());

            if (apiResult.status() == OperationStatusCodes.FAILED) {
                return Optional.empty();
            }

            return Optional.of(new AnnotatedImage(bufferedImage, apiResult.analyzeResult()));
        }

        /**
         * helper method to avoid code duplication: we received a http 429 response from azure. Wait for the specified amount before continuing the thread execution
         *
         * @param e exception thrown by ComputerVisionClient
         */
        private void handleComputerVisionOcrErrorException(ComputerVisionOcrErrorException e) {
            if (e.response().code() == 429) {
                final var retryAfterSeconds = e.response().headers().values("retry-after").stream().map(Integer::parseInt).findFirst();

                if (retryAfterSeconds.isEmpty()) {
                    throw new RuntimeException("could not retrieve delay amount from 429 response", e);
                }

                log.trace("received http status 429, sleeping for {} ms as requested", Duration.ofSeconds(retryAfterSeconds.get()).toMillis());
                try {
                    Thread.sleep(Duration.ofSeconds(retryAfterSeconds.get()).toMillis());
                } catch (InterruptedException ex) {
                    throw new RuntimeException("sleep was interrupted", ex);
                }
                log.trace("woke up");
            } else {
                // we don't know how to handle this
                throw e;
            }
        }

        /**
         * helper method to convert bufferedImage to a byte array in png format with exception handling
         *
         * @param bufferedImage image to be converted
         * @return image encoded in png format as byte array
         */
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

        /**
         * helper method to retrieve operation location form a http response given by `compVisClientImpl.readInStreamWithServiceResponseAsync`
         *
         * @param response response to be analyzed
         * @return operation location contained within response
         */
        private String extractOperationLocationFromResponse(ServiceResponseWithHeaders<Void, ReadInStreamHeaders> response) {
            return response.response().headers().values("operation-location").stream().findFirst().orElse(null);
        }

        /**
         * helper method to extract the operation id from a given operation location
         *
         * @param operationLocation location string to extract id from
         * @return operation id contained in operation location
         */
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

    /**
     * instruct the worker thread to query the azure read api to retrieve annotations for the passed image
     * single worker thread is used to avoid conflicts when encountering api request limits (http status code 429)
     * @param bufferedImage image to be analyzed by read api
     * @return image with annotations added for identified text. May be empty, if read api operation fails
     */
    Optional<AnnotatedImage> getAnnotationsForImage(BufferedImage bufferedImage) {
        final var future = executorService.submit(() -> new AnnotationJob().getAnnotationsForImage(bufferedImage));

        try {
            return future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException("Azure API worker was interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Azure API worker threw ExecutionException", e);
        }
    }

    /**
     * call {@link AzureApiHandler#getAnnotationsForImage(java.awt.image.BufferedImage)} up to `maxTries` times before giving up
     * @param bufferedImage image to be analyzed by read api
     * @param maxTries number of attempts on successfully processing `bufferedImage`
     * @return image with annotations added for identified text
     */
    public AnnotatedImage getAnnotationsForImageWithRetry(BufferedImage bufferedImage, int maxTries) {
        Optional<AnnotatedImage> result = Optional.empty();
        for (int tries = 0; tries < maxTries; tries = tries + 1) {
            result = getAnnotationsForImage(bufferedImage);

            if (result.isPresent()) {
                break;
            } else {
                log.warn("get annotations for image attempt {} failed. retrying", tries);
            }
        }
        return result.orElseThrow(() -> new RuntimeException("could not get annotations for image"));
    }
}
