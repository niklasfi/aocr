# aocr (azure OCR)

Swiftly add ocr layers to scanned pdf files.

Unfortunately existing open source ocr solutions ([tesseract](https://github.com/tesseract-ocr/tesseract)) pale in comparison with the ones commercially available. The [azure read api](https://docs.microsoft.com/en-us/azure/cognitive-services/computer-vision/overview-ocr) provides particularly good results. It is also easy to set up, but while it can annotate text in images, there is no easy way to upload and ocr a full pdf document.

That is, until now. `aocr` provides an easy way to ocr full pdf documents.

## build

```shell
cd $your_git_repo
mvn package
```

## usage

```
usage: aocr
 -e,--endpoint <arg>   azure cognitive services endpoint url
 -i,--input <arg>      path to input pdf file
 -k,--key <arg>        subscription key to access azure cognitive services
 -o,--output <arg>     path to save output to
```

for example:

```shell
./target/aocr 
    -e $your_azure_cognitive_services_endpoint_url \
    -k $your_azure_subscription_key \
    -i $your_input_file \
    -o $your_output_file
```
