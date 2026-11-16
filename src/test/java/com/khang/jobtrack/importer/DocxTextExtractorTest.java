package com.khang.jobtrack.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * .docx からの本文の取り出し（テスト用の小さな .docx をメモリ上で作る）
 */
class DocxTextExtractorTest {

    private final DocxTextExtractor extractor = new DocxTextExtractor();

    private static final String HEAD =
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>";
    private static final String TAIL = "</w:body></w:document>";

    private static ByteArrayInputStream zip(String entryName, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Test
    @DisplayName("段落ごとに改行し、同じ段落の文字はつなげる。タブ・改行も反映")
    void extractsParagraphs() throws IOException {
        String xml = HEAD
                + "<w:p><w:r><w:t>① 12/7(月)</w:t></w:r><w:r><w:t> オンライン</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>募集職種：</w:t><w:tab/><w:t>Webエンジニア</w:t><w:br/><w:t>N2以上</w:t></w:r></w:p>"
                + TAIL;

        String text = extractor.extract(zip("word/document.xml", xml));

        assertThat(text).isEqualTo("① 12/7(月) オンライン\n募集職種：\tWebエンジニア\nN2以上");
    }

    @Test
    @DisplayName("word/document.xml がない（.docx ではない）→ DocumentReadException")
    void notDocx() throws IOException {
        assertThatThrownBy(() -> extractor.extract(zip("hello.txt", "hello")))
                .isInstanceOf(DocumentReadException.class);
    }

    @Test
    @DisplayName("ZIPではないファイル → DocumentReadException")
    void notZip() {
        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(DocumentReadException.class);
    }

    @Test
    @DisplayName("XXE対策：DOCTYPE を含む XML は読まない")
    void rejectsDoctype() throws IOException {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + HEAD + "<w:p><w:r><w:t>&xxe;</w:t></w:r></w:p>" + TAIL;

        assertThatThrownBy(() -> extractor.extract(zip("word/document.xml", xml)))
                .isInstanceOf(DocumentReadException.class);
    }
}
