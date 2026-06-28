package com.workouttracker.auth.email;

/**
 * 이메일 발송 추상화.
 *
 * <p>구현체는 발송 채널(SES/로그 등)만 책임지고, 메일 본문 조립 같은 도메인 로직은 알지 못한다.
 * 인증 메일 본문은 {@link EmailVerificationService} 가 조립해 순수한 to/subject/body 로 넘긴다.</p>
 *
 * <ul>
 *   <li>{@link SesEmailSender} — 운영(prod) 프로필, AWS SES v2 발송</li>
 *   <li>{@link LogEmailSender} — 로컬/테스트(!prod) 프로필, 로그 출력</li>
 * </ul>
 */
public interface EmailSender {

    /**
     * 이메일 발송.
     *
     * @param to      수신 주소
     * @param subject 제목
     * @param body    본문(plain text)
     */
    void send(String to, String subject, String body);
}
