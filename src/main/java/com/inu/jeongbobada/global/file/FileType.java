package com.inu.jeongbobada.global.file;

import java.util.Arrays;
import java.util.Set;

// 업로드를 허용할 수 있는 형식. 호출하는 도메인이 이 중에서 허용 목록을 골라 넘긴다.
// signature는 파일 맨 앞 바이트로, 확장자만 바꾼 파일을 막는 데 쓴다.
// contentTypes는 브라우저가 보낼 수 있는 값들이다. 오피스·한글 파일은 브라우저·OS에 따라 application/octet-stream으로
// 오는 경우가 많아 함께 받는다 (앞부분 바이트로 한 번 더 확인하므로 형식이 다른 파일은 걸러진다).
public enum FileType {

    PDF("pdf", Set.of("pdf"), "application/pdf", Set.of("application/pdf"), bytes('%', 'P', 'D', 'F')),
    JPG("jpg", Set.of("jpg", "jpeg"), "image/jpeg", Set.of("image/jpeg"), bytes(0xFF, 0xD8, 0xFF)),
    PNG("png", Set.of("png"), "image/png", Set.of("image/png"), bytes(0x89, 'P', 'N', 'G')),
    // pptx·docx·xlsx는 zip 형식이라 앞부분 바이트가 같다 (확장자로 형식을 고르고, zip인지만 확인한다)
    PPTX("pptx", Set.of("pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/octet-stream"),
        bytes('P', 'K', 0x03, 0x04)),
    DOCX("docx", Set.of("docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream"),
        bytes('P', 'K', 0x03, 0x04)),
    XLSX("xlsx", Set.of("xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"),
        bytes('P', 'K', 0x03, 0x04)),
    // 한글 문서(HWP 5.0)는 OLE 복합 문서 형식이다
    HWP("hwp", Set.of("hwp"), "application/x-hwp",
        Set.of("application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp", "application/octet-stream"),
        bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1));

    // S3 key와 안내 문구에 쓰는 대표 확장자 (jpeg로 올려도 key는 .jpg)
    private final String extension;
    private final Set<String> extensions;
    // S3에 저장할 때 붙이는 Content-Type (내려받을 때 브라우저가 이 값으로 연다)
    private final String contentType;
    private final Set<String> acceptedContentTypes;
    private final byte[] signature;

    FileType(String extension, Set<String> extensions, String contentType, Set<String> acceptedContentTypes, byte[] signature) {
        this.extension = extension;
        this.extensions = extensions;
        this.contentType = contentType;
        this.acceptedContentTypes = acceptedContentTypes;
        this.signature = signature;
    }

    // 키가 {prefix}/{UUID}.{확장자}라서 key만으로 형식을 알 수 있다 (목록의 "형식" 칸 등)
    public static String extensionOfKey(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    String extension() {
        return extension;
    }

    String contentType() {
        return contentType;
    }

    boolean hasExtension(String ext) {
        return extensions.contains(ext);
    }

    boolean acceptsContentType(String value) {
        return value != null && acceptedContentTypes.contains(value);
    }

    int signatureLength() {
        return signature.length;
    }

    boolean matchesSignature(byte[] head) {
        return Arrays.equals(head, signature);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
