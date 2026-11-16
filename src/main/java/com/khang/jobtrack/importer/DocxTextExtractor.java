package com.khang.jobtrack.importer;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Word ファイル（.docx）から本文のテキストを取り出す。
 * .docx は ZIP ファイルで、本文は word/document.xml に入っている。外部ライブラリを使わず JDK だけで読む。
 *
 * 安全対策：
 * - XXE（XMLの外部エンティティ攻撃）を防ぐため、DOCTYPE を禁止する
 * - ZIP爆弾（展開すると巨大になるファイル）を防ぐため、読み込むサイズに上限を設ける
 */
@Component
public class DocxTextExtractor {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final long MAX_XML_BYTES = 10L * 1024 * 1024; // document.xml は 10MB まで

    public String extract(InputStream docx) {
        byte[] xml = readDocumentXml(docx);
        Document doc = parse(xml);
        StringBuilder sb = new StringBuilder();
        walk(doc.getDocumentElement(), sb);
        // 空行が続きすぎないように整える
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private byte[] readDocumentXml(InputStream docx) {
        try (ZipInputStream zip = new ZipInputStream(docx)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return readLimited(zip);
                }
            }
        } catch (IOException e) {
            throw new DocumentReadException("Wordファイルを読み込めませんでした。", e);
        }
        throw new DocumentReadException("Wordファイル（.docx）ではないようです。");
    }

    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_XML_BYTES) {
                throw new DocumentReadException("ファイルが大きすぎます。");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE対策：DOCTYPE を禁止し、外部の DTD・エンティティを読まない
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new DocumentReadException("Wordファイルの内容を読み取れませんでした。", e);
        }
    }

    // w:t = 文字、w:tab = タブ、w:br / w:cr = 改行、w:p（段落）の終わり = 改行
    private void walk(Node node, StringBuilder sb) {
        if (node.getNodeType() == Node.ELEMENT_NODE && W.equals(node.getNamespaceURI())) {
            switch (node.getLocalName()) {
                case "t" -> {
                    sb.append(node.getTextContent());
                    return;
                }
                case "tab" -> {
                    sb.append('\t');
                    return;
                }
                case "br", "cr" -> {
                    sb.append('\n');
                    return;
                }
                default -> { }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            walk(child, sb);
        }
        if (node.getNodeType() == Node.ELEMENT_NODE && W.equals(node.getNamespaceURI())) {
            if ("p".equals(node.getLocalName())) {
                sb.append('\n');
            } else if ("tc".equals(node.getLocalName())) {
                sb.append('\t'); // 表のセルの区切り
            }
        }
    }
}
