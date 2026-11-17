package com.inu.jeongbobada.global.file;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// endpoint 설정에 따라 서류 열람 주소(presigned URL)가 어느 저장소를 가리키는지 확인한다 (#175).
// 서명만 만들고 네트워크는 쓰지 않는다.
class S3ConfigTest {

    private static final StaticCredentialsProvider FAKE_KEYS =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key", "test-secret-key"));

    @Test
    void endpoint가_있으면_그_주소에_버킷을_경로로_넣은_URL을_만든다() throws Exception {
        AwsProperties properties = new AwsProperties("ap-northeast-2",
            new AwsProperties.S3("documents", "https://abcd.storage.supabase.co/storage/v1/s3"));

        URL url = presign(properties);

        assertThat(url.getHost()).isEqualTo("abcd.storage.supabase.co");
        assertThat(url.getPath()).isEqualTo("/storage/v1/s3/documents/verification/a.pdf");
        assertThat(url.getQuery()).contains("X-Amz-Signature=");
    }

    @Test
    void endpoint가_비어_있으면_AWS_S3_주소를_만든다() throws Exception {
        AwsProperties properties = new AwsProperties("us-east-1", new AwsProperties.S3("documents", " "));

        URL url = presign(properties);

        assertThat(url.getHost()).isEqualTo("documents.s3.amazonaws.com");
        assertThat(url.getPath()).isEqualTo("/verification/a.pdf");
    }

    private static URL presign(AwsProperties properties) throws MalformedURLException {
        try (S3Presigner presigner = new S3Config().s3Presigner(properties, FAKE_KEYS)) {
            FileStorage storage = new FileStorage(null, presigner, properties);
            return URI.create(storage.presignedGetUrl("verification/a.pdf", Duration.ofMinutes(5))).toURL();
        }
    }
}
