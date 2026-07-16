package com.wangzhi.knowledgebase.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class TextExtractionService {

    private final int maxCharacters;
    private final String ocrLanguage;

    public TextExtractionService(
            @Value("${app.import.max-extracted-characters:2000000}") int maxCharacters,
            @Value("${app.import.ocr-language:eng}") String ocrLanguage) {
        this.maxCharacters = maxCharacters;
        this.ocrLanguage = ocrLanguage;
    }

    public ExtractionResult extract(Path path, String originalName) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(maxCharacters);
        Metadata metadata = new Metadata();
        metadata.set("resourceName", originalName);
        ParseContext context = new ParseContext();

        TesseractOCRConfig ocr = new TesseractOCRConfig();
        ocr.setLanguage(ocrLanguage);
        context.set(TesseractOCRConfig.class, ocr);

        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);
        context.set(PDFParserConfig.class, pdf);

        try (InputStream input = Files.newInputStream(path)) {
            parser.parse(input, handler, metadata, context);
        }
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        String text = clean(handler.toString());
        if (text.length() < 10) {
            if (contentType != null && contentType.startsWith("image/")) {
                throw new IllegalArgumentException("图片未识别出有效文字，请确认已安装 Tesseract 及对应语言包");
            }
            throw new IllegalArgumentException("文件未提取出有效文字，可能是扫描件、加密文件或空文档");
        }
        return new ExtractionResult(contentType == null ? "application/octet-stream" : contentType, text);
    }

    private String clean(String text) {
        return text.replace('\u0000', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public record ExtractionResult(String contentType, String text) {}
}
