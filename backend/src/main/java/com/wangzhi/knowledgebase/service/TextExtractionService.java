package com.wangzhi.knowledgebase.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        try (InputStream input = Files.newInputStream(path)) {
            return extract(input, originalName);
        }
    }

    public ExtractionResult extract(InputStream input, String originalName) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        StructuredContentHandler handler = new StructuredContentHandler(maxCharacters);
        Metadata metadata = new Metadata();
        metadata.set("resourceName", originalName);
        ParseContext context = new ParseContext();

        TesseractOCRConfig ocr = new TesseractOCRConfig();
        ocr.setLanguage(ocrLanguage);
        context.set(TesseractOCRConfig.class, ocr);

        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);
        context.set(PDFParserConfig.class, pdf);

        parser.parse(input, handler, metadata, context);
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        List<ParsedBlock> blocks = handler.blocks();
        String text = clean(blocks.stream().map(ParsedBlock::text).reduce("", (left, right) ->
                left.isBlank() ? right : left + "\n\n" + right));
        if (text.length() < 10) {
            if (contentType != null && contentType.startsWith("image/")) {
                throw new IllegalArgumentException("图片未识别出有效文字，请确认已安装 Tesseract 及对应语言包");
            }
            throw new IllegalArgumentException("文件未提取出有效文字，可能是扫描件、加密文件或空文档");
        }
        return new ExtractionResult(contentType == null ? "application/octet-stream" : contentType, text, blocks);
    }

    private String clean(String text) {
        return text.replace('\u0000', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public record ExtractionResult(String contentType, String text, List<ParsedBlock> blocks) {}

    private static final class StructuredContentHandler extends DefaultHandler {

        private static final Set<String> SIMPLE_BLOCKS = Set.of("p", "li", "pre", "blockquote");

        private final int maxCharacters;
        private final List<ParsedBlock> blocks = new ArrayList<>();
        private final String[] headings = new String[6];
        private StringBuilder current;
        private String captureTag;
        private String blockType;
        private int pageNumber;
        private int characters;

        private StructuredContentHandler(int maxCharacters) {
            this.maxCharacters = maxCharacters;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tag = tag(localName, qName);
            if ("div".equals(tag) && containsClass(attributes, "page")) {
                pageNumber++;
            }
            if (current != null) {
                if ("br".equals(tag)) {
                    current.append('\n');
                } else if ("td".equals(tag) || "th".equals(tag)) {
                    if (!current.isEmpty() && !Character.isWhitespace(current.charAt(current.length() - 1))) {
                        current.append(" | ");
                    }
                }
                return;
            }
            if (tag.matches("h[1-6]")) {
                begin(tag, "heading");
            } else if (SIMPLE_BLOCKS.contains(tag)) {
                begin(tag, tag.equals("li") ? "list-item" : "paragraph");
            } else if ("tr".equals(tag)) {
                begin(tag, "table-row");
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (current == null || length == 0) {
                return;
            }
            characters += length;
            if (characters > maxCharacters) {
                throw new SAXException("文档提取内容超过最大字符限制：" + maxCharacters);
            }
            current.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String tag = tag(localName, qName);
            if (current == null || !tag.equals(captureTag)) {
                return;
            }
            String value = normalize(current.toString());
            if (!value.isBlank()) {
                int page = Math.max(1, pageNumber);
                if (captureTag.matches("h[1-6]")) {
                    int level = captureTag.charAt(1) - '1';
                    headings[level] = value;
                    Arrays.fill(headings, level + 1, headings.length, null);
                }
                String headingPath = Arrays.stream(headings)
                        .filter(item -> item != null && !item.isBlank())
                        .reduce((left, right) -> left + " > " + right).orElse("");
                blocks.add(new ParsedBlock(blockType, value, page, headingPath,
                        "page:%d#block:%d".formatted(page, blocks.size() + 1)));
            }
            current = null;
            captureTag = null;
            blockType = null;
        }

        private void begin(String tag, String type) {
            current = new StringBuilder();
            captureTag = tag;
            blockType = type;
        }

        private List<ParsedBlock> blocks() {
            return List.copyOf(blocks);
        }

        private String tag(String localName, String qName) {
            String value = localName == null || localName.isBlank() ? qName : localName;
            int colon = value.indexOf(':');
            return (colon >= 0 ? value.substring(colon + 1) : value).toLowerCase(Locale.ROOT);
        }

        private boolean containsClass(Attributes attributes, String expected) {
            String value = attributes.getValue("class");
            return value != null && Arrays.asList(value.split("\\s+")).contains(expected);
        }

        private String normalize(String value) {
            return value.replace('\u0000', ' ')
                    .replaceAll("[ \\t]+", " ")
                    .replaceAll(" ?\\| ?\\| ?", " | ")
                    .replaceAll("\\n{3,}", "\\n\\n")
                    .trim();
        }
    }
}
