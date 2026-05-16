-- =====================================================
-- V2: 운동 종류 마스터 데이터 시드 (12종)
-- 출처: docs/design.md 2.6 시드 데이터
-- 멱등성: ON CONFLICT (code) DO NOTHING
-- =====================================================
INSERT INTO exercises (code, name_ko, name_en, body_part, category) VALUES
    ('BENCH_PRESS',      '벤치프레스',            'Bench Press',             'CHEST',    'COMPOUND'),
    ('INCLINE_DB_PRESS', '인클라인 덤벨프레스',   'Incline Dumbbell Press',  'CHEST',    'COMPOUND'),
    ('DEADLIFT',         '데드리프트',            'Deadlift',                'BACK',     'COMPOUND'),
    ('LAT_PULLDOWN',     '랫풀다운',              'Lat Pulldown',            'BACK',     'COMPOUND'),
    ('BARBELL_ROW',      '바벨로우',              'Barbell Row',             'BACK',     'COMPOUND'),
    ('SQUAT',            '바벨 스쿼트',           'Barbell Squat',           'LEG',      'COMPOUND'),
    ('LEG_PRESS',        '레그프레스',            'Leg Press',               'LEG',      'COMPOUND'),
    ('OHP',              '오버헤드프레스',        'Overhead Press',          'SHOULDER', 'COMPOUND'),
    ('LATERAL_RAISE',    '사이드 레터럴',         'Lateral Raise',           'SHOULDER', 'ISOLATION'),
    ('BB_CURL',          '바벨컬',                'Barbell Curl',            'ARM',      'ISOLATION'),
    ('TRICEPS_PUSHDOWN', '트라이셉스 푸시다운',   'Triceps Pushdown',        'ARM',      'ISOLATION'),
    ('PLANK',            '플랭크',                'Plank',                   'CORE',     'ISOLATION')
ON CONFLICT (code) DO NOTHING;
