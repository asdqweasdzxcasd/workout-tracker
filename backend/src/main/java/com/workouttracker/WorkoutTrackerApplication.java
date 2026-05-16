package com.workouttracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * workout-tracker 백엔드 애플리케이션 진입점.
 *
 * <p>Day 1 단계: 스캐폴딩만 구성. 실제 비즈니스 로직은 Day 2부터 추가됨.
 */
@SpringBootApplication
public class WorkoutTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkoutTrackerApplication.class, args);
    }
}
