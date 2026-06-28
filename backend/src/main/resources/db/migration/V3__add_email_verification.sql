-- =====================================================
-- V3: 이메일 인증 컬럼 추가 (D.2 자체 가입 이메일 인증)
-- 출처: architect 설계 D.2
-- 대상: PostgreSQL 16
-- =====================================================

-- users 에 이메일 인증 여부 컬럼 추가 (기본 FALSE)
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 사용자 전원 백필: 인증 기능 도입 전 가입자는 모두 인증된 것으로 간주
-- (로그인 차단 대상에서 제외). 이 마이그레이션 시점에 존재하는 모든 행이 대상이므로 WHERE TRUE.
UPDATE users SET email_verified = TRUE WHERE TRUE;
