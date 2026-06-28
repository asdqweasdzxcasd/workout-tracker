package com.workouttracker.auth.email;

/**
 * 회원가입 완료 이벤트.
 *
 * <p>{@code AuthService.signup()} 의 트랜잭션이 커밋된 뒤 인증 메일을 발송하기 위한 신호.
 * 발송 실패가 가입 트랜잭션을 롤백시키지 않도록, 코드 생성/저장/발송은 커밋 이후에 수행한다
 * (설계 7번 트랜잭션/동시성).
 *
 * @param email    가입 이메일
 * @param nickname 가입 닉네임(메일 본문 인사말용)
 */
public record UserSignedUpEvent(String email, String nickname) {}
