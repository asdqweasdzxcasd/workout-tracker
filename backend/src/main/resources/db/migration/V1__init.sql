-- =====================================================
-- V1: 초기 스키마
-- 출처: docs/design.md 2.2 DDL
-- 대상: PostgreSQL 16
-- =====================================================

-- =========================================
-- users
-- =========================================
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    nickname        VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users (LOWER(email));

-- =========================================
-- exercises (마스터 데이터)
-- =========================================
CREATE TABLE exercises (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name_ko     VARCHAR(50)  NOT NULL,
    name_en     VARCHAR(80)  NOT NULL,
    body_part   VARCHAR(20)  NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_exercises_body_part ON exercises (body_part) WHERE is_active = TRUE;

-- =========================================
-- workout_sessions
-- =========================================
CREATE TABLE workout_sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    performed_on    DATE        NOT NULL,
    memo            VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sessions_user_date ON workout_sessions (user_id, performed_on DESC, id DESC);

-- =========================================
-- session_exercises
-- =========================================
CREATE TABLE session_exercises (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id),
    order_no    INT    NOT NULL,
    UNIQUE (session_id, order_no)
);
CREATE INDEX idx_session_exercises_session ON session_exercises (session_id);
CREATE INDEX idx_session_exercises_exercise ON session_exercises (exercise_id);

-- =========================================
-- exercise_sets
-- =========================================
CREATE TABLE exercise_sets (
    id                   BIGSERIAL PRIMARY KEY,
    session_exercise_id  BIGINT  NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE,
    set_no               INT     NOT NULL,
    weight_kg            NUMERIC(6, 2) NOT NULL CHECK (weight_kg >= 0),
    reps                 INT     NOT NULL CHECK (reps > 0),
    UNIQUE (session_exercise_id, set_no)
);
CREATE INDEX idx_exercise_sets_weight ON exercise_sets (session_exercise_id, weight_kg DESC);

-- =========================================
-- session_photos
-- =========================================
CREATE TABLE session_photos (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT      NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    s3_key        VARCHAR(300) NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    BIGINT       NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_photos_session ON session_photos (session_id);
