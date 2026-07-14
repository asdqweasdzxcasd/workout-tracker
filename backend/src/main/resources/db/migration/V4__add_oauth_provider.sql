-- =====================================================
-- V4: OAuth 소셜 로그인 (D.3) — provider 컬럼 추가 + password_hash NULL 허용
-- 출처: openspec/changes/oauth-social-login (design.md 결정 5)
-- 대상: PostgreSQL 16
-- =====================================================

-- 소셜 제공자 식별 컬럼. 로컬 가입자는 둘 다 NULL.
ALTER TABLE users ADD COLUMN provider    VARCHAR(20);
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);

-- 소셜 가입자는 비밀번호가 없다 → NOT NULL 제약 해제.
-- (로컬 가입자는 애플리케이션 레벨에서 여전히 password_hash 를 채운다.)
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- 카카오는 이메일이 선택 동의(비즈앱 필요)라 이메일 없는 소셜 가입자가 존재할 수 있다
-- → email NOT NULL 해제 (설계 Open Question 결정). UNIQUE 는 유지 — PostgreSQL 은
--   UNIQUE 에서 NULL 다중 허용이므로 이메일 없는 사용자 여러 명이어도 충돌 없음.
--   로컬 가입(D.2)은 애플리케이션 검증이 이메일을 필수로 요구하므로 영향 없다.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- (provider, provider_id) 유니크: 같은 소셜 계정으로 중복 가입 방지.
-- PostgreSQL 은 UNIQUE 제약에서 NULL 을 서로 다른 값으로 취급하므로,
-- provider/provider_id 가 모두 NULL 인 로컬 가입자 다수는 충돌하지 않는다.
ALTER TABLE users ADD CONSTRAINT uq_users_provider UNIQUE (provider, provider_id);
