package com.workouttracker.auth.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원가입 후 인증 메일 발송 리스너.
 *
 * <p>가입 트랜잭션 <b>커밋 이후</b>({@link TransactionPhase#AFTER_COMMIT})에만 발송한다 →
 * 발송 실패(SES/Redis 장애)가 가입을 롤백시키지 않는다. 또한 {@link Async} 로 별도 스레드에서
 * 실행해 가입 응답을 블로킹하지 않는다(설계 7번).
 *
 * <p>발송 중 예외는 가입에 영향이 없어야 하므로 여기서 흡수하고 로그만 남긴다. 사용자는 코드를
 * 받지 못하면 재발송 엔드포인트로 복구할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventListener {

    private final EmailVerificationService emailVerificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSignedUp(UserSignedUpEvent event) {
        try {
            emailVerificationService.issueAndSend(event.email(), event.nickname());
        } catch (Exception e) {
            // 발송 실패는 가입과 무관 — 삼켜서 로그만. 사용자는 재발송으로 복구 가능.
            log.error("가입 후 인증 메일 발송 실패: email={}", event.email(), e);
        }
    }
}
