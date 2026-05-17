package com.workouttracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 클라이언트 / Presigner 빈 등록.
 *
 * <p>출처: docs/design.md 5.4 S3 Presigned URL 워크플로우, 7.8 S3 보안
 *
 * <p>자격증명 전략:
 * <ul>
 *   <li>{@link DefaultCredentialsProvider} - 환경변수(개발) → 인스턴스 프로파일(EC2 IAM Role)
 *       순서로 자동 탐색</li>
 *   <li>운영 EC2 에는 IAM Role 부착으로 AccessKey 노출 없이 자격증명 주입</li>
 *   <li>로컬 개발은 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 환경변수 사용
 *       (docs/AWS_S3_SETUP.md 참고)</li>
 * </ul>
 *
 * <p>S3Client / S3Presigner 모두 thread-safe → 싱글톤 빈으로 등록.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
