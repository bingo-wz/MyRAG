package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.service.TextExtractionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MultiFormatExtractionTest {

    private final TextExtractionService extractionService = new TextExtractionService(100_000, "eng");

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractWordPdfAndExcel() throws Exception {
        Path word = createWord();
        Path pdf = createPdf();
        Path excel = createExcel();

        assertThat(extractionService.extract(word, word.getFileName().toString()).text()).contains("warranty policy");
        assertThat(extractionService.extract(pdf, pdf.getFileName().toString()).text()).contains("Return policy");
        assertThat(extractionService.extract(excel, excel.getFileName().toString()).text()).contains("Coupon rule");
    }

    private Path createWord() throws Exception {
        Path path = tempDir.resolve("policy.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText("Official warranty policy provides one year of service.");
            document.write(output);
        }
        return path;
    }

    private Path createPdf() throws Exception {
        Path path = tempDir.resolve("return-policy.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Return policy allows eligible products within seven days.");
                content.endText();
            }
            document.save(path.toFile());
        }
        return path;
    }

    private Path createExcel() throws Exception {
        Path path = tempDir.resolve("coupons.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(path)) {
            var sheet = workbook.createSheet("Rules");
            sheet.createRow(0).createCell(0).setCellValue("Coupon rule");
            sheet.createRow(1).createCell(0).setCellValue("Only one platform coupon per order");
            workbook.write(output);
        }
        return path;
    }
}
