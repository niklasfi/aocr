# aocr (azure OCR)

Swiftly add ocr layers to scanned pdf files.

Unfortunately existing open source ocr solutions ([tesseract](https://github.com/tesseract-ocr/tesseract)) pale in
comparison with the ones commercially available.
The [azure read api](https://docs.microsoft.com/en-us/azure/cognitive-services/computer-vision/overview-ocr) provides
particularly good results. It is also easy to set up, but while it can annotate text in images, there is no easy way to
upload and ocr a full pdf document.

That is, until now. `aocr` provides an easy way to ocr full pdf documents.

## usage

`aocr` can be predominantly used in two ways: It can either be called from a shell as an ELF binary on linux, or it can
function as a java API library for reuse in other projects.

### API

#### maven

Add the following snippet to your dependencies:

```
<dependency>
  <groupId>de.niklasfi.aocr</groupId>
  <artifactId>aocr</artifactId>
  <version>1.2</version>
</dependency>
```

#### API

To call `aocr`, you first have to construct an instance of `de.niklasfi.aocr.AzurePdfOcr`. It can be constructed like so:

```
final var pdfImageRetriever = new PdfImageExtractor(); // or alternatively: new PdfImageRenderer();
final var pdfIoUtil = new PdfIoUtil();
final var fileUtil = new FileUtil();
final var throttlerInterval = AzurePdfOcr.LIMIT_INTERVAL_FREE_TIER // or alternatively LIMIT_INTERVAL_PAID_TIER

final var azurePdfOcr =
        new AzurePdfOcr(azureEndpoint, azureSubscriptionKey, pdfImageRetriever, pdfIoUtil, fileUtil,
                        throttlerInterval);
```

Once `azurePdfOcr` has been constructed, it can be used to apply ocr using the `ocr*` methods. 

### command line

#### build

```shell
cd $your_git_repo
mvn package
```

#### usage

```
usage: aocr
 -e,--endpoint <arg>   azure cognitive services endpoint url
 -i,--input <arg>      path to input pdf file
 -k,--key <arg>        subscription key to access azure cognitive services
 -o,--output <arg>     path to save output to
 -p,--paid             azure subscription is on paid tier
```

for example:

```shell
./target/aocr 
    -e $your_azure_cognitive_services_endpoint_url \
    -k $your_azure_subscription_key \
    -i $your_input_file \
    -o $your_output_file
```
