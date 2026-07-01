package com.workouttracker.auth.email;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * 이메일 인증 오케스트레이션 서비스 (D.2).
 *
 * <ul>
 *   <li>코드 생성/저장/발송: {@link #issueAndSend(String, String)} — SecureRandom 6자리, SHA-256
 *       해시로 Redis 저장(평문 금지), 본문 조립 후 {@link EmailSender} 발송.</li>
 *   <li>검증: {@link #verify(String, String)} — 입력 코드 해시 대조, 성공 시 조건부 UPDATE 로
 *       멱등·원자적 활성화. brute-force 는 실패 카운터로 차단.</li>
 *   <li>재발송: {@link #resend(String)} — 60초 쿨다운 + 시간당 상한. 이메일 열거 방어를 위해
 *       미가입/이미인증에도 동일하게 반환(실제 발송만 조건부).</li>
 * </ul>
 *
 * <p>이 서비스는 user/auth/인프라(Redis/SES)에만 의존하며, 운동 도메인(session 등)을
 * import 하지 않는다.</p>
 */
@Slf4j
@Service
public class EmailVerificationService {

    /** 코드 입력 실패 허용 횟수 — 초과 시 코드 폐기 + 429. */
    private static final int MAX_ATTEMPTS = 5;
    /** 시간당 재발송 상한. */
    private static final int MAX_RESENDS_PER_HOUR = 5;
    /** 6자리 숫자 코드의 상한(미만) — [0, 1_000_000). */
    private static final int CODE_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final EmailVerificationStore store;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String appName;
    /**
     * E2E 테스트 전용 평문 코드 기록기 — {@code !prod} 에서만 빈이 존재한다.
     * prod 에는 빈 자체가 없어 {@code Optional.empty()} 로 주입되며, 기록은 no-op 이 된다.
     * (평문 코드가 prod 어디에도 저장되지 않도록 보장하는 이중 안전장치.)
     */
    private final Optional<TestVerificationCodeRecorder> testCodeRecorder;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationStore store,
                                    EmailSender emailSender,
                                    @Value("${spring.application.name:workout-tracker}") String appName,
                                    Optional<TestVerificationCodeRecorder> testCodeRecorder) {
        this.userRepository = userRepository;
        this.store = store;
        this.emailSender = emailSender;
        this.appName = appName;
        this.testCodeRecorder = testCodeRecorder;
    }

    /**
     * 인증 코드 생성 → Redis 저장(해시) → 메일 발송.
     *
     * <p>가입 직후 이벤트 리스너 및 재발송 경로가 공통으로 사용한다. 실패 카운터는 새 코드 발급과
     * 함께 의미가 없어지므로 기존 코드/카운터를 덮어쓴다(같은 키, TTL 갱신).
     *
     * @param email    수신 이메일
     * @param nickname 본문 인사말용 닉네임(없으면 null 허용)
     */
    public void issueAndSend(String email, String nickname) {
        // 정규화: Redis 키 생성과 발송 대상 기준을 DB(LOWER 매칭)와 통일.
        String normalizedEmail = normalize(email);
        String code = generateCode();
        store.saveCode(normalizedEmail, sha256(code));

        // E2E 전용: !prod 에서만 평문 코드를 임시 기록(prod 엔 빈이 없어 no-op).
        testCodeRecorder.ifPresent(recorder -> recorder.record(normalizedEmail, code));

        String subject = "[%s] 이메일 인증 코드".formatted(appName);
        String body = buildBody(nickname, code);
        emailSender.send(normalizedEmail, subject, body);
        log.info("인증 코드 발송: email={}", normalizedEmail);
    }

    /**
     * 인증 코드 검증 및 활성화.
     *
     * <p>이미 인증된 사용자는 멱등 처리(예외 없이 반환). 코드가 없으면(미발급/만료) 만료로 간주.
     * 불일치 시 실패 카운터를 올리고, 상한 초과면 코드를 폐기한 뒤 429 를 던진다.
     *
     * @throws BusinessException INVALID_VERIFICATION_CODE / VERIFICATION_CODE_EXPIRED / TOO_MANY_ATTEMPTS
     */
    @Transactional
    public void verify(String email, String code) {
        // 정규화: Redis 키 조회와 DB 조회/UPDATE 기준을 통일(대소문자 변형으로 키 miss/우회 방지).
        String normalizedEmail = normalize(email);
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        // 이미 인증된 사용자는 멱등 200 (코드 조회/소모 없이 종료).
        if (userOpt.isPresent() && userOpt.get().isEmailVerified()) {
            return;
        }

        Optional<String> storedHash = store.findCodeHash(normalizedEmail);
        if (storedHash.isEmpty()) {
            // 코드 미발급이거나 TTL 만료 → 만료로 통일 응답.
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        if (!constantTimeEquals(storedHash.get(), sha256(code))) {
            long attempts = store.incrementAttempt(normalizedEmail);
            if (attempts > MAX_ATTEMPTS) {
                // brute-force 방어: 코드 폐기 → 재발송을 강제.
                store.deleteCode(normalizedEmail);
                log.warn("인증 코드 실패 횟수 초과 → 코드 폐기: email={} attempts={}", normalizedEmail, attempts);
                throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS);
            }
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 코드 일치 → 일회성 소모 후 조건부 UPDATE(멱등·원자적).
        store.deleteCode(normalizedEmail);
        int updated = userRepository.activateEmailVerification(normalizedEmail, OffsetDateTime.now());
        log.info("이메일 인증 완료: email={} updatedRows={}", normalizedEmail, updated);
        // updated==0 이면 대상 사용자가 없거나 이미 인증된 경우 — 어느 쪽이든 200 멱등 처리.
    }

    /**
     * 인증 코드 재발송.
     *
     * <p>이메일 열거 방어: 미가입/이미인증 이메일에도 호출자는 동일한 결과(202)를 받는다.
     * 실제 발송은 (1) 가입된 사용자이고 (2) 아직 미인증일 때만 수행한다.
     *
     * <p>레이트리밋: 60초 쿨다운(원자적 NX 잠금) + 시간당 상한. 어느 한쪽이라도 초과하면
     * {@link ErrorCode#RESEND_RATE_LIMITED} 를 던진다(429). 레이트리밋은 발송 대상이 아닌
     * 이메일에는 적용하지 않아, 카운터로 가입 여부가 노출되지 않게 한다.
     *
     * @throws BusinessException RESEND_RATE_LIMITED
     */
    public void resend(String email) {
        // 정규화: 레이트리밋 Redis 키와 DB 조회 기준을 통일(대소문자 변형으로 상한 우회 방지).
        String normalizedEmail = normalize(email);
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        boolean shouldSend = userOpt.isPresent() && !userOpt.get().isEmailVerified();
        if (!shouldSend) {
            // 미가입 또는 이미 인증 — 조용히 종료(열거 방어). 레이트리밋 카운터도 건드리지 않는다.
            log.info("재발송 요청(발송 대상 아님, 열거 방어로 202 응답): email={}", normalizedEmail);
            return;
        }

        // 60초 쿨다운 — 원자적 SET NX EX.
        if (!store.tryAcquireResendCooldown(normalizedEmail)) {
            throw new BusinessException(ErrorCode.RESEND_RATE_LIMITED);
        }
        // 시간당 상한.
        if (!store.incrementHourlyResendAndCheck(normalizedEmail, MAX_RESENDS_PER_HOUR)) {
            throw new BusinessException(ErrorCode.RESEND_RATE_LIMITED);
        }

        issueAndSend(normalizedEmail, userOpt.get().getNickname());
    }

    /**
     * 이메일 정규화 — 검증 흐름의 Redis 키와 DB 조회 기준을 단일화한다.
     *
     * <p>DB 는 {@code LOWER(email)} 함수 인덱스로 대소문자 무관 매칭을 하는데, Redis 키는
     * 원본 문자열을 그대로 쓰면 대소문자 변형으로 키 miss(정상 사용자 인증 실패) 및
     * 레이트리밋/실패 카운터 우회가 가능하다. 모든 public 진입점에서 이 헬퍼로 통일한다.
     */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** SecureRandom 6자리 숫자 코드. 앞자리 0 을 보존하기 위해 0-패딩 문자열로 반환. */
    private String generateCode() {
        int value = secureRandom.nextInt(CODE_BOUND);
        return String.format("%06d", value);
    }

    /** 인증 메일 본문 조립. EmailSender 는 본문을 모르게, 조립은 여기서만. */
    private String buildBody(String nickname, String code) {
        String greeting = (nickname == null || nickname.isBlank())
                ? "안녕하세요."
                : "%s 님, 안녕하세요.".formatted(nickname);
        return greeting + "\n\n"
                + "아래 인증 코드를 입력해 이메일 인증을 완료해주세요.\n\n"
                + "    인증 코드: " + code + "\n\n"
                + "이 코드는 10분간 유효합니다.\n"
                + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.\n";
    }

    /** SHA-256 해시(16진 소문자). 평문 코드를 저장하지 않기 위한 단방향 변환. */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 표준 JDK 에 항상 존재 — 도달 불가.
            throw new IllegalStateException("SHA-256 미지원 환경", e);
        }
    }

    /** 타이밍 공격 방지를 위한 상수 시간 문자열 비교. */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
