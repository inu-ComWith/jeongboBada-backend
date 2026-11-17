package com.inu.jeongbobada.global.file;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

// 자격 증명은 설정 파일이 아니라 SDK 기본 방식으로 찾는다.
// 로컬·Learner Lab은 환경변수(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN) 또는 ~/.aws/credentials,
// Supabase Storage는 대시보드에서 발급한 S3 access key를 같은 환경변수로 넣는다(세션 토큰 없음). EC2는 인스턴스 역할.
// 키는 S3를 실제로 호출할 때 찾으므로, 키가 없는 CI·compose 스모크에서도 서버는 뜬다.
@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class S3Config {

    @Bean(destroyMethod = "close")
    public DefaultCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    // endpoint가 있으면 S3 호환 저장소(Supabase Storage)로 붙는다. 이런 저장소는 버킷을 호스트 이름이 아니라
    // 경로에 넣는 방식(path-style)만 받으므로 함께 켠다. presigned URL도 같은 주소·방식으로 만들어야 열린다
    @Bean(destroyMethod = "close")
    public S3Client s3Client(AwsProperties properties, AwsCredentialsProvider credentialsProvider) {
        S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider);
        if (properties.s3().hasEndpoint()) {
            builder.endpointOverride(URI.create(properties.s3().endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(AwsProperties properties, AwsCredentialsProvider credentialsProvider) {
        S3Presigner.Builder builder = S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider);
        if (properties.s3().hasEndpoint()) {
            builder.endpointOverride(URI.create(properties.s3().endpoint()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
