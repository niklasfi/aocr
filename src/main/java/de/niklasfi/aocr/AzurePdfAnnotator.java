package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.dto.AnalyzeResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

@Slf4j
public class AzurePdfAnnotator {

    public void addPageToDocument(PDDocument pdDocument, BufferedImage bufferedImage) {
        final var pdPage = blankPageFromBufferedImage(pdDocument, bufferedImage);
        addBufferedImageToPage(pdDocument, pdPage, bufferedImage);
    }

    public void addPageToDocument(PDDocument pdDocument, AnnotatedImage annotatedImage) {
        final var pdPage = blankPageFromBufferedImage(pdDocument, annotatedImage.bufferedImage());
        addBufferedImageToPage(pdDocument, pdPage, annotatedImage.bufferedImage());
        annotatedImage.analyzeResult().ifPresent(result ->
            addAnalyzeResultsToPage(pdDocument, pdPage, result)
        );
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

    private String stripEncoding(String text, PDType1Font font) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < text.length(); ++idx) {
            if (font.getEncoding().contains(text.charAt(idx))) {
                sb.append(text.charAt(idx));
            } else {
                log.warn("cannot encode {}. skipping this char", text.codePointAt(idx));
            }
        }

        return sb.toString();
    }

    private void addAnalyzeResultsToPage(PDDocument pdDocument, PDPage pdPage, AnalyzeResult analyzeResult) {
        final var font = PDType1Font.HELVETICA;

        // width and height of pdf page
        final var wp = pdPage.getMediaBox().getWidth();
        final var hp = pdPage.getMediaBox().getHeight();

        try (final var cs = new PDPageContentStream(pdDocument, pdPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.setFont(font, FONT_SIZE);

            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(0.f);
            cs.setGraphicsStateParameters(graphicsState);

            for (final var readResult : analyzeResult.readResults()) {
                // width and height of azure image
                final var wa = readResult.width();
                final var ha = readResult.height();

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
                    final var textStripped = stripEncoding(line.text(), font);
                    final var bb = line.boundingBox();

                    final var azurePoints = List.of(
                            new Point2D.Float(bb.get(BB_TL_X).floatValue(), bb.get(BB_TL_Y).floatValue()),
                            new Point2D.Float(bb.get(BB_TR_X).floatValue(), bb.get(BB_TR_Y).floatValue()),
                            new Point2D.Float(bb.get(BB_BR_X).floatValue(), bb.get(BB_BR_Y).floatValue()),
                            new Point2D.Float(bb.get(BB_BL_X).floatValue(), bb.get(BB_BL_Y).floatValue())
                    );

                    final var pdfPoints = azurePoints.stream()
                            .map(point -> (Point2D.Float) azurePdfAffineTransform.transform(point, null))
                            .toList();

                    // vector along the horizontal of pdf coordinates
                    final var vh = new Point2D.Float(
                            (pdfPoints.get(1).x - pdfPoints.get(0).x + pdfPoints.get(2).x - pdfPoints.get(3).x) / 2,
                            (pdfPoints.get(1).y - pdfPoints.get(0).y + pdfPoints.get(2).y - pdfPoints.get(3).y) / 2
                    );

                    // vector along the vertical of the pdf coordinates
                    final var vv = new Point2D.Float(
                            (pdfPoints.get(0).x - pdfPoints.get(3).x + pdfPoints.get(1).x - pdfPoints.get(2).x) / 2,
                            (pdfPoints.get(0).y - pdfPoints.get(3).y + pdfPoints.get(1).y - pdfPoints.get(2).y) / 2
                    );

                    // projection of vv onto vh
                    final var vvPrjFactor = (vh.x * vv.x + vh.y * vv.y) / (float) vh.distanceSq(0, 0);
                    final var vvPrj = new Point2D.Float(vh.x * vvPrjFactor, vh.y * vvPrjFactor);
                    // remainder after projection, a.k.a. height
                    final var targetHeight = (float) new Point2D.Float(vv.x - vvPrj.x, vv.y - vvPrj.y).distance(0, 0);

                    // length of vh is target width
                    final var targetWidth = (float) new Point2D.Float(vh.x, vh.y).distance(0, 0);

                    // angle of vh with respect to the x-axis
                    final var angle = Math.atan2(vh.y, vh.x);

                    // bottom left-hand corner of pdf text box
                    final var offset = pdfPoints.get(3);

                    // https://stackoverflow.com/questions/13701017/calculation-string-width-in-pdfbox-seems-only-to-count-characters
                    // https://stackoverflow.com/questions/17171815/get-the-font-height-of-a-character-in-pdfbox
                    final var baseTextWidth = font.getStringWidth(textStripped) / 1000 * FONT_SIZE;
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
                    cs.showText(textStripped);
                    cs.endText();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("could not create page content stream", e);
        }
    }
}
