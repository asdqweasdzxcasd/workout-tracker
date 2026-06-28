package com.workouttracker.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬/테스트용 {@link EmailSender} 구현 (prod 외 모든 프로필).
 *
 * <p>실제 메일을 발송하지 않고 본문을 로그로 출력한다. 인증 코드는 본문에 포함되므로,
 * 로컬 개발 시 애플리케이션 로그에서 코드를 확인할 수 있다.</p>
 */
@Slf4j
@Component
@Profile("!prod")
public class LogEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[LogEmailSender] 이메일 발송(모의)\n  to: {}\n  subject: {}\n  body:\n{}",
                to, subject, body);
    }
}
