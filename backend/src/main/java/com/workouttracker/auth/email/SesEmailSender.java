package com.workouttracker.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.nio.charset.StandardCharsets;

/**
 * 운영용 {@link EmailSender} 구현 (prod 프로필) — AWS SES v2 발송.
 *
 * <p>발신 주소는 설정값({@code aws.ses.from-address})으로 주입한다. 운영에서는 SSM
 * Parameter Store {@code /workout-tracker/SES_FROM_ADDRESS} 가 환경변수로 매핑된다.
 * 자격증명은 {@link com.workouttracker.config.SesConfig} 의 IAM Role 기반 클라이언트가 담당한다.
 *
 * <p>이 클래스는 순수 발송만 책임진다 — 본문(인증 코드 포함)은
 * {@link EmailVerificationService} 가 조립해 넘긴다.
 */
@Slf4j
@Component
@Profile("prod")
public class SesEmailSender implements EmailSender {

    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private final SesV2Client sesV2Client;
    private final String fromAddress;

    public SesEmailSender(SesV2Client sesV2Client,
                          @Value("${aws.ses.from-address}") String fromAddress) {
        this.sesV2Client = sesV2Client;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().charset(CHARSET).data(subject).build())
                                .body(Body.builder()
                                        .text(Content.builder().charset(CHARSET).data(body).build())
                                        .build())
                                .build())
                        .build())
                .build();

        sesV2Client.sendEmail(request);
        log.info("[SesEmailSender] 인증 메일 발송 완료: to={}", to);
    }
}
