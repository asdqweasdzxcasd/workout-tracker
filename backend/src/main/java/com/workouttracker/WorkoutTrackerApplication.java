package com.workouttracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * workout-tracker 백엔드 애플리케이션 진입점.
 *
 * <p>Phase 1 단계: 스캐폴딩만 구성. 실제 비즈니스 로직은 Phase 2부터 추가됨.
 *
 * <p>{@code @EnableAsync}: 가입 후 인증 메일 발송을 별도 스레드에서 비동기 처리하기 위함(D.2).
 */
@EnableAsync
@SpringBootApplication
public class WorkoutTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkoutTrackerApplication.class, args);
    }
}
