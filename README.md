# workout-tracker

운동 세션/세트 기록 + 인증샷 업로드를 다루는 풀스택 MVP. 1주(40시간) 안에 면접 시연용으로 완성하는 것이 목표.

- Backend: Java 17 + Spring Boot 3.3 (REST API)
- Frontend: Next.js 16 (App Router) + TypeScript + Tailwind
- DB: PostgreSQL 16 (로컬은 docker-compose, 운영은 RDS 예정)
- 배포 예정: Vercel(FE BFF) + EC2 Docker(BE) + RDS + S3

상세 설계, 일정, 트레이드오프는 [`docs/design.md`](./docs/design.md)를 참고.

---

## 로컬 vs 운영 환경

| 항목 | 로컬 (local) | 운영 (prod) |
|---|---|---|
| 프로필 | `application-local.yml` | `application-prod.yml` |
| Spring Boot 실행 | `./gradlew bootRun` (호스트) | Docker 컨테이너 (`deploy/docker-compose.prod.yml`) |
| DB | `docker-compose.local.yml` 의 PostgreSQL 컨테이너 | AWS RDS PostgreSQL 16 |
| 이미지 저장소 | (사용 안 함 or 로컬 IAM 키) | AWS S3 (`silee-workout-tracker-photos`) |
| 시크릿 | `.env.local` (git 제외) | `deploy/.env` (git 제외) |
| 자동 재시작 | 수동 | `restart: unless-stopped` |

운영 배포 절차는 [`deploy/DEPLOY.md`](./deploy/DEPLOY.md) 참고.

---

## 로컬 실행 방법

사전 요구:

- Java 17 (Temurin/Adoptium 권장)
- Node.js 22 LTS 이상
- Docker Desktop

### 1) 환경변수 파일 준비

```bash
cp .env.local.example .env.local
# 필요 시 비밀번호 등 수정
```

### 2) DB + Adminer 기동

```bash
docker compose -f docker-compose.local.yml up -d
```

- PostgreSQL: `localhost:5432` (DB: `workout_tracker`, User: `workout`)
- Adminer: http://localhost:8081 (System=PostgreSQL, Server=postgres)

### 3) 백엔드 기동

```bash
cd backend
./gradlew bootRun         # macOS / Linux
gradlew.bat bootRun       # Windows
```

- 부트 시 Flyway가 `V1__init.sql`, `V2__seed_exercises.sql`을 자동 적용
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html (Day 2 이후 엔드포인트가 채워짐)

기본 프로필은 `local`. 다른 프로필을 쓰려면 `SPRING_PROFILES_ACTIVE=xxx` 환경변수.

### 4) 프론트엔드 기동

```bash
cd frontend
npm install
npm run dev
```

- http://localhost:3000 접속 → "workout-tracker" 텍스트 확인

### 5) 종료

```bash
docker compose -f docker-compose.local.yml down
# 데이터까지 삭제: docker compose -f docker-compose.local.yml down -v
```

---

## 디렉토리 구조 (요약)

```
workout-tracker/
├── backend/                       # Spring Boot
│   ├── src/main/java/com/workouttracker/
│   │   ├── auth/  user/  exercise/  session/  photo/  common/  config/
│   │   └── WorkoutTrackerApplication.java
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-local.yml
│       └── db/migration/{V1__init.sql, V2__seed_exercises.sql}
├── frontend/                      # Next.js 16 (App Router)
│   └── src/{app, components, features, lib, types}
├── docs/
│   └── design.md                  # 설계/일정 단일 소스
├── docker-compose.local.yml
├── .env.local.example
└── README.md
```

전체 트리는 `docs/design.md` 부록 B 참고.

---

## 7일 일정 (요약)

| Day | 내용 |
|---|---|
| 1 | 인프라/뼈대 (스캐폴딩, Flyway, docker-compose) |
| 2 | 인증 + 운동 종류 API |
| 3 | 세션 도메인 (CRUD, 단일 트랜잭션) |
| 4 | Frontend 인증/목록 + BFF 프록시 |
| 5 | PR/통계 + S3 인증샷 (presigned URL) |
| 6 | AWS 배포 (EC2/RDS/S3 + GitHub Actions) |
| 7 | E2E (Playwright) + 문서화 |

세부 내용은 [`docs/design.md`](./docs/design.md) 6장 참고.

---

## 트러블슈팅

- `./gradlew bootRun` 시 DB 연결 에러 → `docker compose ps` 로 postgres 컨테이너 healthy 여부 확인
- 8080 포트 충돌 → Spring Boot가 사용하므로 다른 서비스 종료 또는 `server.port` 변경
- Flyway 마이그레이션 실패 후 재실행 → 개발 중이라면 `docker compose down -v` 로 볼륨 삭제 후 재기동
