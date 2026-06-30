package com.workouttracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * AWS SES v2 클라이언트 빈 등록 (운영 프로필 전용).
 *
 * <p>출처: architect 설계 D.2 — 이메일 인증 코드 발송.
 *
 * <p>자격증명 전략은 {@link S3Config} 와 동일하게 {@link DefaultCredentialsProvider} 를 사용한다.
 * 운영 EC2 에는 IAM Role 을 부착해 AccessKey 노출 없이 자격증명을 주입한다(하드코딩 금지).
 *
 * <p>리전은 {@code aws.ses.region}(없으면 {@code aws.region})으로 주입한다. SES 발신 도메인이
 * S3 와 다른 리전에 있을 수 있어 별도 키로 오버라이드 가능하게 둔다.
 *
 * <p>{@code SesV2Client} 는 thread-safe → 싱글톤 빈으로 등록.
 */
@Configuration
@Profile("prod")
public class SesConfig {

    @Bean
    public SesV2Client sesV2Client(@Value("${aws.ses.region:${aws.region}}") String region) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
