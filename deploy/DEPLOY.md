# workout-tracker 배포 가이드

## 현재 운영 구조 — V2 (ECS Fargate)

| 컴포넌트 | 실체 |
|---|---|
| Frontend | Vercel (Next.js 16, BFF 패턴) — https://workout-tracker-ten-zeta.vercel.app |
| Backend | **AWS ECS Fargate** (workout-tracker-cluster) — 2 task (0.5 vCPU / 1GB each) |
| Load Balancer | AWS ALB (`workout-tracker-alb`) + Target Group (`workout-tracker-tg-fargate`, **target type=ip**) |
| DB | AWS RDS PostgreSQL 16 |
| 스토리지 | AWS S3 (`<S3-BUCKET>`) |
| 시크릿 | **AWS SSM Parameter Store** (SecureString) — DB_PASSWORD / JWT_SECRET / DB_URL / DB_USERNAME |
| 컨테이너 이미지 | **ECR Private** (`workout-tracker-backend`) — GitHub Actions 가 매 main push 시 자동 build/push |
| 배포 인증 | **OIDC** (GitHub Actions ↔ AWS) — long-lived AccessKey 미사용 |
| 배포 | `git push origin main` → `.github/workflows/deploy-ecs.yml` 자동 실행 |

V1 (EC2 + Docker Compose) 도 코드 보존 — 자세한 비교는 본 문서 § 12 참조.

## 진행한 Phase 요약

| Phase | 내용 | 상태 |
|---|---|---|
| 6.1 | RDS PostgreSQL 생성 + SG 설정 | ✅ |
| 6.2 | Spring Boot 컨테이너 단일 배포 | ✅ |
| 6.3 | EC2 IAM Role 부착, AccessKey 환경변수 제거 | ✅ |
| 6.4 | ALB + Target Group + Blue/Green 2 컨테이너 + Rolling 배포 스크립트 | ✅ |
| 6.5 | Vercel 프론트엔드 배포 (BFF 환경변수 연동) | ✅ |
| 7   | Playwright E2E 11 시나리오 (+ S3 업로드 1건 `test.fixme` 스킵) + GitHub Actions CI 자동화 | ✅ |
| **8** | **ECS Fargate 마이그레이션 (V1→V2): ECR + SSM + 3 IAM Role + OIDC + 새 ip-type TG + Service Rolling Update** | ✅ |

---

## 1. 초기 셋업 (최초 1회)

### 1.1 소스 체크아웃

```bash
mkdir -p ~/projects
cd ~/projects
git clone <레포 URL> workout-tracker
cd workout-tracker
```

### 1.2 .env 작성

```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```

채워야 할 값 (Phase 6.3 IAM Role 적용 후 기준):

| 항목 | 값 | 비고 |
|---|---|---|
| `DB_URL` | RDS JDBC URL | `jdbc:postgresql://<RDS-ENDPOINT>:5432/workout_tracker` |
| `DB_USERNAME` | RDS 마스터 사용자명 | 기본 `workout` |
| `DB_PASSWORD` | RDS 마스터 비밀번호 | RDS 생성 시 설정한 값 |
| `JWT_SECRET` | 64자 이상 랜덤 문자열 | `openssl rand -base64 64` 로 생성 |
| `AWS_REGION` | AWS 리전 | 기본 `ap-northeast-2` |
| `AWS_S3_BUCKET` | S3 버킷명 | 부트 시 필수 (default 없음) |

> `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` 는 **불필요**. EC2 인스턴스에 `WorkoutTrackerEC2Role` IAM Role 이 부착되어 S3 SDK 가 IMDS 에서 임시 자격증명을 자동 획득한다.

### 1.3 보안 점검

```bash
# .env 파일 권한 600 으로 (소유자만 읽기/쓰기)
chmod 600 deploy/.env

# .env 가 git 추적 대상이 아닌지 확인
git status   # deploy/.env 가 안 나와야 정상
```

---

## 2. 빌드 + 기동

```bash
cd ~/projects/workout-tracker/deploy

# 빌드 + 백그라운드 기동
docker compose --env-file .env -f docker-compose.prod.yml up -d --build

# 처음 빌드는 Gradle 의존성 다운로드로 5~10분 소요 가능
# t3.small 메모리 (2GB) 한계로 빌드 중 OOM 발생 시 swap 추가 권장:
#   sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
#   sudo chmod 600 /swapfile
#   sudo mkswap /swapfile && sudo swapon /swapfile
```

---

## 3. 로그 확인

```bash
# 컨테이너 로그 실시간 추적
docker compose -f docker-compose.prod.yml logs -f workout-tracker-backend

# 또는 컨테이너 이름으로 직접
docker logs -f workout-tracker-backend

# 최근 100줄만
docker logs --tail 100 workout-tracker-backend
```

부팅 성공 신호:

- `Started WorkoutTrackerApplication in N seconds` (Spring Boot 기동 완료)
- `Flyway: Successfully validated N migrations` (DB 연결 + 마이그레이션 OK)
- `Tomcat started on port(s): 8080` (HTTP 리스닝)

---

## 4. 재시작 / 중지

```bash
# 재시작
docker compose -f docker-compose.prod.yml restart

# 중지 + 컨테이너 제거 (이미지는 유지)
docker compose -f docker-compose.prod.yml down

# 중지 + 컨테이너 + 이미지 제거 (디스크 정리)
docker compose -f docker-compose.prod.yml down --rmi local
```

---

## 5. 업데이트 배포 (소스 변경 시)

### 5.1 무중단 (권장) — `rolling-deploy.sh`

```bash
cd ~/workout-tracker/deploy
./rolling-deploy.sh
```

```mermaid
sequenceDiagram
    autonumber
    actor Dev as 개발자
    participant Script as rolling-deploy.sh
    participant ALB
    participant Blue as 🟦 Blue (8080)
    participant Green as 🟩 Green (8081)

    Dev->>Script: ./rolling-deploy.sh
    Note over ALB,Green: 평상시 Blue+Green 50/50

    rect rgb(230, 240, 255)
        Note over Script,Green: Phase 1 — Blue 교체
        Script->>ALB: deregister Blue
        Note over ALB,Green: Green 100% 트래픽
        Script->>Script: drain 30s (in-flight 처리)
        Script->>Blue: docker compose up<br/>--force-recreate --build
        Blue-->>Script: /actuator/health 200
        Script->>ALB: register Blue
        Note over Blue,Green: 양쪽 50/50 (잠시 v1+v2 mixed)
    end

    rect rgb(230, 255, 230)
        Note over Script,Green: Phase 2 — Green 교체 (동일 절차)
        Script->>ALB: deregister Green
        Note over ALB,Blue: Blue 100% 트래픽
        Script->>Script: drain 30s
        Script->>Green: docker compose up<br/>--force-recreate --build
        Green-->>Script: /actuator/health 200
        Script->>ALB: register Green
    end

    Note over Blue,Green: ✅ 배포 완료 — 양쪽 v2, 다운타임 0초
```

자동 실행 흐름:

1. `git pull` (최신 코드)
2. Blue 컨테이너를 ALB Target Group 에서 deregister → 트래픽 차단
3. drain 30초 대기 (in-flight 요청 처리 완료)
4. Blue 컨테이너 새 이미지로 재시작
5. 호스트 헬스체크 통과 대기 (`/actuator/health` 200)
6. Blue 다시 register → ALB healthy 확인
7. Green 에 동일 절차 반복

전체 소요: 약 5~7 분. **중간에 ALB 가 한 컨테이너로 모든 트래픽을 라우팅하므로 다운타임 0초.**

> 수동 검증: 다른 터미널에서 `watch -n 1 'curl -s -o /dev/null -w "%{http_code}\n" http://<ALB-DNS>/actuator/health'` 띄워두고 배포 중 200 응답 유지 확인.

### 5.2 일괄 재시작 (다운타임 허용 시)

```bash
cd ~/workout-tracker
git pull
cd deploy
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
```

두 컨테이너 동시 재기동 → 부팅 동안 약 60~90초 다운.

---

## 6. 헬스체크 검증

```bash
# EC2 내부에서
curl http://localhost:8080/actuator/health
# 예상 응답: {"status":"UP"}

# 외부에서 (SG 8080 인바운드 허용 시)
curl http://<EC2_퍼블릭_IP>:8080/actuator/health

# 컨테이너 상태
docker compose -f docker-compose.prod.yml ps
# STATUS 가 (healthy) 이어야 정상
```

API 동작 확인:

```bash
# 회원가입
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Secret1234!","nickname":"test"}'

# 로그인 -> 토큰 확인
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Secret1234!"}'
```

---

## 7. 트러블슈팅

### 7.1 빌드 실패 / OOM

t3.small 메모리(2GB)로 Gradle 빌드 중 OOM 가능:

```bash
# Gradle 캐시 정리
docker compose -f docker-compose.prod.yml build --no-cache

# 그래도 안 되면 swap 추가
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 영구화: /etc/fstab 에 추가
echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
```

### 7.2 런타임 OOM

```bash
# 메모리 사용량 모니터링
docker stats workout-tracker-backend

# Dockerfile 의 JAVA_TOOL_OPTIONS 조정
# 현재: -Xmx384m -XX:MaxMetaspaceSize=128m
# 부족하면 mem_limit (docker-compose) 도 함께 증액
```

### 7.3 RDS 연결 실패

`HikariPool... Connection is not available` 또는 `Could not open JDBC Connection`:

```bash
# 1) SG 확인: RDS SG 인바운드 5432 에 EC2 SG 가 허용되어야 함
#    AWS Console -> RDS -> 인스턴스 -> Connectivity & security -> VPC security groups
# 2) RDS 엔드포인트가 .env 의 DB_URL 과 일치하는지
# 3) 마스터 사용자명/비밀번호 일치하는지
# 4) DB 존재 여부 (workout_tracker 데이터베이스)
psql -h <RDS_ENDPOINT> -U workout -d workout_tracker
# RDS 엔드포인트는 AWS Console → RDS → 인스턴스 → "Endpoint & port" 에서 확인
```

### 7.4 Flyway 실패

마이그레이션 충돌 시:

```bash
# 컨테이너에 접속해서 Flyway 상태 확인
docker exec -it workout-tracker-backend sh
# 안에서:
# tail /app/app.log 또는 docker logs 로 어느 V__파일 충돌인지 확인
```

### 7.5 S3 / IAM 에러

`software.amazon.awssdk.services.s3.model.S3Exception: Access Denied`:

- EC2 인스턴스에 `WorkoutTrackerEC2Role` IAM Role 이 부착됐는지 (Console → EC2 → 인스턴스 → 보안 → IAM 역할)
- 해당 Role 에 `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` 권한 있는지 (`<S3-BUCKET>/*`)
- IMDSv2 토큰으로 자격증명 확인:
  ```bash
  TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  curl -H "X-aws-ec2-metadata-token: $TOKEN" \
    http://169.254.169.254/latest/meta-data/iam/security-credentials/
  ```
- 버킷 region 이 `ap-northeast-2` 인지 (region 불일치 시 SignatureDoesNotMatch)

### 7.6 컨테이너가 (unhealthy) 로 표시

```bash
# healthcheck 로그 확인
docker inspect workout-tracker-backend --format '{{json .State.Health}}' | jq

# 가장 흔한 원인:
# - DB 연결 실패로 Spring Boot 부팅 자체 실패 (Actuator 응답 안 함)
# - start_period 60s 보다 부팅이 더 오래 걸림 (대용량 의존성 + cold JVM)
```

---

## 8. IAM Role (Phase 6.3) 권한 구성

EC2 인스턴스에 부착된 IAM Role: `WorkoutTrackerEC2Role`

| 정책 | Action | Resource | 용도 |
|---|---|---|---|
| inline `workout-tracker-s3` | `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` | `arn:aws:s3:::<S3-BUCKET>/*` | 인증샷 업로드/다운로드 presigned URL 서명 + 세션 삭제 시 S3 객체 정리 |
| inline `workout-tracker-elb-rolling-deploy` | `elasticloadbalancing:DescribeTargetGroups`, `DescribeTargetHealth`, `RegisterTargets`, `DeregisterTargets` | `*` (TG 1개라 좁힐 의미 적음) | Rolling 배포 스크립트가 ALB 조작 |

IAM Role 부착 후:
- `.env` 에서 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` 제거 가능
- AWS SDK 가 `DefaultCredentialsProvider` 로 IMDS (`169.254.169.254`) 에서 임시 자격증명 자동 획득
- 자격증명 회전 자동, 누출 위험 감소

## 9. ALB / Target Group (Phase 6.4)

| 항목 | 값 |
|---|---|
| ALB 이름 | `workout-tracker-alb` |
| ALB DNS | `<ALB-DNS>` (실제 값은 `.env` / Vercel 환경변수에만) |
| Target Group | `workout-tracker-tg` (HTTP/8080, target type=instance) |
| 등록 대상 | 같은 EC2 인스턴스 (`<EC2_INSTANCE_ID>`) 의 8080 / 8081 두 포트 |
| 헬스체크 경로 | `/actuator/health` (HTTP 200 기대) |
| 헬스체크 주기 | 30초, 임계값 healthy/unhealthy 모두 2 |

### 보안 그룹 인바운드

| SG | 인바운드 | 출처 |
|---|---|---|
| `alb-sg` | TCP 80 | 0.0.0.0/0 (인터넷) |
| `web-sg` (EC2) | TCP 8080, 8081 | `alb-sg` (Source SG 지정, CIDR 아님) |
| `db-sg` (RDS) | TCP 5432 | `web-sg` |

> `web-sg` 인바운드를 ALB SG 로만 좁힌 결과, EC2 퍼블릭 IP 로 직접 8080/8081 접근 불가 → **ALB 만이 유일한 진입점**.

## 10. Vercel (Phase 6.5)

| 항목 | 값 |
|---|---|
| Project | `workout-tracker` |
| Root Directory | `frontend/` (모노레포라 명시) |
| Framework | Next.js 16 (자동 감지) |
| 환경변수 `EC2_API_URL` | `http://<ALB-DNS>` |
| 환경변수 `NEXT_PUBLIC_API_BASE_URL` | `/api/proxy` |

브라우저 → Vercel HTTPS → 서버사이드 fetch → ALB HTTP. **서버사이드 호출은 CORS 무관**, Mixed Content 발생 안 함.

---

## 11. GitHub Actions E2E CI (Phase 7)

### 11.1 워크플로우 구성

| 항목 | 값 |
|---|---|
| 파일 | `.github/workflows/e2e.yml` |
| Runner | `ubuntu-latest` (GitHub-hosted) |
| 트리거 | `push` to `main`, `pull_request` to `main`, `workflow_dispatch` (수동) |
| 시간 | 약 3~4 분 (캐시 적중 시) / 8~10 분 (첫 실행) |

### 11.2 실행 흐름

```mermaid
flowchart TB
    Push([📌 git push to main])
    Trigger[GitHub Actions<br/>e2e.yml 감지]

    Push --> Trigger --> Runner

    subgraph Runner["🖥️ Ubuntu Runner (깨끗한 VM)"]
        direction TB

        subgraph Services["services container"]
            PG[("PostgreSQL 16")]
        end

        S1[Checkout] --> S2[Setup JDK 17<br/>+ Gradle 캐시]
        S2 --> S3[Setup Node 20<br/>+ npm 캐시]
        S3 --> S4[bootRun &<br/>백그라운드 실행]
        S4 --> S5{"/actuator/health<br/>200?"}
        S5 -->|polling 최대 5분| S5
        S5 -->|✅| S6[npm ci]
        S6 --> S7[.env.local 동적 생성<br/>EC2_API_URL=localhost:8080]
        S7 --> S8[Playwright Chromium 설치]
        S8 --> S9[npm run test:e2e<br/>11 시나리오]
        S4 -.JDBC.-> PG
    end

    Runner --> Outcome{통과?}
    Outcome -->|✅| Green([🟢 뱃지 + README 갱신])
    Outcome -->|❌| Artifact[Artifacts 업로드 always]

    Artifact --> A1[playwright-report.zip<br/>HTML 리포트]
    Artifact --> A2[playwright-test-results.zip<br/>trace.zip + 스크린샷 + 비디오]
    Artifact --> A3[backend.log<br/>Spring Boot 전체 로그]
```

### 11.3 환경변수

워크플로우 job 레벨에 정의 (GitHub Secrets 불필요 — 모두 로컬 더미값):

```yaml
env:
  DB_URL: jdbc:postgresql://localhost:5432/workout_tracker
  DB_USERNAME: workout
  DB_PASSWORD: workout_pw_local
  JWT_SECRET: ci-dummy-secret-at-least-32-characters-long-for-hs256-algorithm-OK
  SPRING_PROFILES_ACTIVE: local
  CI: "true"
```

AWS S3 자격증명은 의도적으로 미주입. 사진 업로드 시나리오는 `test.fixme` 로 스킵 처리되어 CI 에서는 호출되지 않음.

### 11.4 모니터링 / 결과 확인

```bash
# 최근 실행 목록
gh run list --workflow=e2e.yml --limit 5

# 진행 중 실행 실시간 watch
gh run watch <RUN_ID> --exit-status

# 특정 실행 상세 (실패 step 확인)
gh run view <RUN_ID> --log-failed

# artifact 다운로드 (실패 디버깅)
gh run download <RUN_ID>
```

또는 브라우저: `https://github.com/<owner>/<repo>/actions`

### 11.5 실패 시 디버깅 절차

1. **Artifact 다운로드** → `playwright-report.zip` 압축 해제 → `index.html` 열기
2. 실패한 테스트에서 **Trace** 버튼 → Time-travel DOM snapshot 으로 시점별 상태 확인
3. `playwright-test-results/<test>/error-context.md` 가 자동 생성 — 페이지 snapshot + Test source + Error details 한 곳에 정리
4. 백엔드가 원인일 가능성이 있으면 `backend.log` 확인 (Spring Boot 전체 로그)

### 11.6 trace 보는 법

```bash
cd frontend
npx playwright show-trace <path/to/trace.zip>
```

또는 HTML 리포트에서 실패 테스트의 "Trace" 버튼 클릭 → 브라우저에서 즉시 열림.

Trace Viewer 가 보여주는 것:
- **Actions timeline** — 각 click/fill 시점
- **DOM snapshot** — 그 시점의 페이지 상태 (이전/이후 비교 가능)
- **Network** — 요청/응답 헤더와 본문
- **Console** — 브라우저 콘솔 메시지
- **Source** — 실패 라인이 강조된 테스트 코드

### 11.7 5 라운드 디버깅 사례 (실제 진행 기록)

```mermaid
timeline
    title E2E 디버깅 5 라운드 진화
    Round 1 — 5/11  : 초기 워크플로우 셋업
                    : PostgreSQL + JDK17 + Node20 + bootRun + Playwright
    Round 2 — 10/11 : ★ useAuthGuard hydration race (production 잠재 버그 발견!)
                    : Next.js 16 __next-route-announcer__ 와 strict mode 충돌
                    : mounted 플래그 + 텍스트 셀렉터로 수정
    Round 3 — 10/11 : 셀렉터 정규식 가정 오류 (^60 으로 시작 X)
                    : listitem 텍스트 패턴 단순화
    Round 4 — 10/11 : 중첩 listitem strict mode (부모 li 가 자식 텍스트 포함)
                    : hasNot 으로 leaf li 만 매칭
    Round 5 — 11/11 ✅ : waitForResponse race window
                       : promise 등록을 navigation 이전으로 이동
```

| 라운드 | 통과/실패 | 원인 | Fix |
|---|---|---|---|
| 1 | 5/11 | 초기 셋업 | 워크플로우 파일 작성 |
| 2 | 10/11 | (1) `useAuthGuard` hydration race — 정상 로그인 사용자가 /login 으로 일시 튕김 (잠재 prod 버그) (2) Next.js 16 의 `__next-route-announcer__` 와 `getByRole("alert")` strict mode 충돌 | (1) `mounted` 플래그로 client mount 보장 (2) `getByText` 로 변경 |
| 3 | 10/11 | 셀렉터 정규식 가정 오류 (`^60` 으로 시작) | 셀렉터 단순화 |
| 4 | 10/11 | 중첩 listitem strict mode (부모 li 가 자식 텍스트 포함) | `hasNot` 으로 leaf li 만 매칭 |
| 5 | **11/11 ✅** | `waitForResponse` promise 등록이 fetch 시작 후라 응답 missed | promise 를 navigation 이전에 등록 |

#### Round 2 핵심: hydration race (production 잠재 버그)

E2E 가 노출한 진짜 prod 버그. `useSyncExternalStore` 의 `getServerSnapshot` 이 항상 false 라 hydration 첫 commit 에서 정상 로그인 사용자도 일시적으로 /login 으로 튕길 수 있는 race:

```mermaid
sequenceDiagram
    autonumber
    participant Server as SSR
    participant Browser
    participant Store as useSyncExternalStore
    participant Effect as useEffect
    participant Router

    Server->>Browser: HTML 응답<br/>(getServerSnapshot → hasToken=false)
    Browser->>Browser: React hydration 시작

    rect rgb(255, 230, 230)
        Note over Browser,Router: ⚠️ Race — useEffect commit 순서 결정적이지 않음
        Effect->>Effect: 첫 commit (hasToken=false 라고 판단)
        Effect->>Router: router.replace("/login") ⚡
        Store->>Store: getClientSnapshot → hasToken=true (localStorage)
        Note over Store: 너무 늦음, 이미 navigate 시작
    end

    Note over Browser,Router: 사용자는 정상 로그인인데 /login 으로 튕김

    rect rgb(220, 250, 220)
        Note over Browser,Router: ✅ Fix — mounted 플래그
        Effect->>Effect: 첫 commit: setMounted(true)
        Effect->>Effect: 다음 commit: (mounted=true, hasToken=true)
        Note over Effect: 조건 (mounted && !hasToken) 불충족 → redirect 안 함
    end
```

### 11.8 Deprecation 알림 (2026-09-16 까지 대응 필요)

Action 들이 Node.js 20 런타임 위에서 동작 중. 2026-09-16 이후 강제 제거. 대응 옵션:

- **Opt-in 즉시**: workflow 의 `env:` 에 `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: "true"` 추가
- **버전 업그레이드 대기**: `actions/checkout@v5` 등 Node 24 지원 버전 출시 시 교체

---

## 12. V1 → V2 마이그레이션 (EC2 + Compose → ECS Fargate)

### 12.1 왜 옮겼나

V1 의 한계:
- **인스턴스 SPOF**: 단일 EC2 — AZ 장애 시 전체 다운, 인스턴스 패치/업그레이드 직접
- **자가 치유 없음**: 컨테이너가 죽으면 `restart: unless-stopped` 만, EC2 가 죽으면 끝
- **수평 확장 불가**: Auto Scaling 없음, 트래픽 폭증 시 대응 불가
- **시크릿이 EC2 의 `.env` 파일에 평문 보관** — 인스턴스 접근 권한자 누구나 읽기 가능
- **배포 자동화가 EC2 안의 bash 스크립트**: GitHub Actions 가 SSH 로 진입해 `git pull` + `rolling-deploy.sh` 호출하는 구조 (없거나 손으로 SSH)

V2 가 해결:
- ECS Service 가 desired count 유지 — task 가 죽으면 자동 재시작
- Multi-AZ Subnet 4개 → AZ 장애 시에도 다른 AZ 의 task 가 계속 서비스
- (옵션) Service Auto Scaling 으로 CPU/요청 기반 자동 스케일
- SSM Parameter Store SecureString — KMS 로 암호화, 코드/`.env` 에 평문 0
- GitHub Actions OIDC → ECS API 직접 호출, SSH 불필요

### 12.2 변경 사항 한눈에

| 항목 | V1 | V2 |
|---|---|---|
| 컴퓨팅 | EC2 t3.small × 1대 (인스턴스 관리) | ECS Fargate × 2 task (서버리스) |
| 컨테이너 오케스트레이션 | docker-compose | ECS Service |
| 배포 트리거 | EC2 SSH + `./rolling-deploy.sh` | `git push` → GitHub Actions |
| 배포 메커니즘 | bash 스크립트 (deregister → drain → restart → register) | ECS Rolling Update (maximumPercent=200%) |
| 컨테이너 이미지 저장소 | EC2 안에서 `docker build` 직접 | **ECR Private repo** (`workout-tracker-backend`) |
| Target Group 타입 | `instance` (EC2 8080 / 8081) | **`ip`** (Fargate awsvpc ENI) |
| 시크릿 보관 | `deploy/.env` (EC2 디스크) | **SSM Parameter Store SecureString** |
| 시크릿 주입 | docker-compose `env_file` | Task Definition `secrets[]` (ARN 참조) |
| EC2 IAM Role | `WorkoutTrackerEC2Role` (S3 + ELB) | 분리됨 (아래 3개) |
| GitHub Actions 인증 | AccessKey 없음 (배포 안 함) | **OIDC** (long-lived 자격증명 없음) |
| GitHub 변수 | 없음 (EC2 안 `.env` 가 진실) | **GitHub Variables 7개** (ARN/이름만) |
| 로그 | `docker logs` (EC2 디스크) | CloudWatch Logs `/ecs/workout-tracker-backend` |
| ALB Listener default forward | `workout-tracker-tg` (instance) | `workout-tracker-tg-fargate` (ip) |

### 12.3 IAM Role 3종 분리 (최소 권한 원칙)

```mermaid
flowchart LR
    GHA["🤖 GitHub Actions<br/>(deploy-ecs.yml)"]
    GhRole["🔑 WorkoutTrackerGithubDeployRole<br/>(OIDC trust)"]
    TER["🔑 WorkoutTrackerEcsTaskExecutionRole"]
    TR["🔑 WorkoutTrackerEcsTaskRole"]

    ECR[("🐳 ECR<br/>workout-tracker-backend")]
    ECS["☁️ ECS Service<br/>UpdateService API"]
    SSM[("🔐 SSM Parameter Store<br/>/workout-tracker/*")]
    CW[("📋 CloudWatch Logs<br/>/ecs/workout-tracker-*")]
    S3[("📷 S3 인증샷 버킷")]

    GHA -->|sts:AssumeRoleWithWebIdentity| GhRole
    GhRole -->|ecr:PutImage| ECR
    GhRole -->|ecs:UpdateService<br/>iam:PassRole| ECS

    TER -->|ecr:GetDownloadUrlForLayer| ECR
    TER -->|ssm:GetParameters<br/>kms:Decrypt| SSM
    TER -->|logs:CreateLogGroup<br/>logs:PutLogEvents| CW

    TR -->|s3:PutObject<br/>s3:GetObject<br/>s3:DeleteObject| S3
```

| Role | 누가 사용? | 권한 |
|---|---|---|
| **`WorkoutTrackerGithubDeployRole`** | GitHub Actions (OIDC) | ECR push, ECS UpdateService, `iam:PassRole`(2개 Role 한정) |
| **`WorkoutTrackerEcsTaskExecutionRole`** | ECS 가 task 시작할 때 | ECR pull, CloudWatch Logs 작성, SSM Parameter 읽기, KMS 복호화 |
| **`WorkoutTrackerEcsTaskRole`** | 컨테이너 안의 애플리케이션 | S3 Put/Get/DeleteObject (인증샷) |

### 12.4 시크릿 흐름 (SSM Parameter Store)

```mermaid
sequenceDiagram
    autonumber
    participant Dev as 운영자
    participant SSM as SSM Parameter Store
    participant TDF as Task Definition
    participant ECS as ECS Service
    participant Task as Fargate Task<br/>(Spring Boot)

    Note over Dev,SSM: ① 시크릿 등록 (1회)
    Dev->>SSM: PutParameter<br/>/workout-tracker/DB_PASSWORD (SecureString, KMS alias/aws/ssm)
    Dev->>SSM: PutParameter<br/>/workout-tracker/JWT_SECRET (SecureString)
    Dev->>SSM: PutParameter<br/>/workout-tracker/DB_URL (String)
    Dev->>SSM: PutParameter<br/>/workout-tracker/DB_USERNAME (String)

    Note over TDF: ② Task Definition 에는 ARN 만
    Note over TDF: secrets[] = [<br/>  { name: "DB_PASSWORD",<br/>    valueFrom: "arn:...parameter/workout-tracker/DB_PASSWORD" }<br/>]

    Note over ECS,Task: ③ Task 시작 시 자동 주입
    ECS->>Task: RunTask
    Task->>SSM: GetParameters (TaskExecutionRole 으로)
    SSM-->>Task: 복호화된 값
    Task->>Task: env 변수로 주입<br/>(컨테이너 시작 전)
    Task->>Task: Spring Boot 부팅<br/>${DB_PASSWORD} 등 해석
```

이로써:
- 코드/git/Vercel 환경변수/GitHub Variables 어디에도 평문 시크릿 0
- Task Definition 에 ARN 만 있음 (public 해도 안전)
- 시크릿 변경 시 SSM Parameter 만 업데이트 → 다음 task 재시작에서 자동 반영

### 12.5 GitHub Actions 자동 배포 흐름

```mermaid
flowchart TB
    Push([📌 git push origin main])
    GHA["🤖 GitHub Actions<br/>.github/workflows/deploy-ecs.yml"]

    Push --> GHA

    subgraph CICD["GHA Runner (Ubuntu)"]
        direction TB
        S1[Checkout] --> S2[OIDC AssumeRole<br/>WorkoutTrackerGithubDeployRole]
        S2 --> S3[ECR Login]
        S3 --> S4[docker build + push<br/>tags: :SHA, :latest]
        S4 --> S5[render task-definition.json<br/>placeholder 치환]
        S5 --> S6[RegisterTaskDefinition<br/>새 revision]
        S6 --> S7[UpdateService<br/>+ wait-for-service-stability]
    end

    GHA --> CICD
    CICD --> ECS["☁️ ECS Service Rolling Update<br/>새 task 2개 띄움 → 옛 task drain"]
    ECS --> ALB["🟢 healthy 후 ALB Target Group 자동 등록"]
```

### 12.6 마이그레이션 중 만난 함정 5가지 (talking point)

V1 → V2 마이그레이션 진행 중 ECS Deployment Circuit Breaker / 가짜 GitHub Actions fail 이 반복 발생. 각각의 원인 + 해결:

#### 함정 1: ALB Target Group 의 target type mismatch
**증상**: ECS Service 생성 마법사에서 옛 `workout-tracker-tg` (instance 타입) 선택 시 "Fargate awsvpc 모드와 호환 안 됨" 경고.

**원인**: Fargate task 는 EC2 인스턴스가 아니라 ENI (탄력적 네트워크 인터페이스) 를 부여받는 awsvpc 모드. ALB Target Group 이 task IP 를 등록할 수 있도록 `target type = ip` 여야 함.

**해결**: 새 Target Group `workout-tracker-tg-fargate` (ip 타입) 생성 + ALB Listener default forward 를 새 TG 로 변경. V1 의 옛 TG (`workout-tracker-tg`, instance) 는 그대로 보존.

#### 함정 2: Task Execution Role 에 `logs:CreateLogGroup` 누락
**증상**: 첫 배포 시 ECS task 가 `PROVISIONING` 에서 `STOPPED (TaskFailedToStart)` 로 즉시 떨어짐.
```
ResourceInitializationError: failed to validate logger args:
... AccessDeniedException: User: WorkoutTrackerEcsTaskExecutionRole
is not authorized to perform: logs:CreateLogGroup
```

**원인**: 관리형 정책 `AmazonECSTaskExecutionRolePolicy` 는 **기존** log group 에 대한 `PutLogEvents` 만 허용. Task Definition 에 `awslogs-create-group: "true"` 옵션을 쓰면 ECS 가 log group 을 동적으로 생성하려 하는데 이 권한이 없음.

**해결**: Task Execution Role 의 인라인 정책 `workout-tracker-ssm-read` 에 다음 Statement 추가:
```json
{
  "Sid": "CloudWatchLogsCreateGroup",
  "Effect": "Allow",
  "Action": ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"],
  "Resource": "arn:aws:logs:*:*:log-group:/ecs/workout-tracker-*:*"
}
```

#### 함정 3: RDS Security Group 이 ECS task SG 차단
**증상**: 권한 fix 후 task 가 컨테이너까지는 시작하지만 부팅 도중 Flyway 가 RDS 접속 실패로 죽음.
```
Caused by: org.postgresql.util.PSQLException: The connection attempt failed.
Caused by: java.net.SocketTimeoutException: Connect timed out
```

**원인**: RDS 의 `workout-tracker-rds-sg` 인바운드 규칙이 V1 시절의 `web-sg` (EC2) 만 허용. 새 `ecs-tasks-sg` (Fargate) 는 RDS 가 모름 → SG drop.

**해결**: RDS SG 인바운드 규칙에 새 규칙 추가:
```
유형: PostgreSQL  포트: 5432  소스: ecs-tasks-sg (sg-xxxxx)
```

#### 함정 4: ALB 활성 AZ ≠ ECS Service Subnet AZ (미스매치)
**증상**: 권한/SG 문제 다 풀린 뒤에도 매 배포마다 Deployment Circuit Breaker 가 발동해 자동 롤백. ECS Service Events 에 다음이 반복:
```
service workout-tracker-backend-svc task ... port 8080 is unhealthy
in target-group workout-tracker-tg-fargate
due to (reason Target is in an Availability Zone that is not enabled for the load balancer.)
```

**원인**:
- ECS Service 의 네트워크 설정에 서브넷 4개 (4개 AZ 분산)
- ALB 는 가용 영역 2개 (apne2-az1, apne2-az3) 만 활성화
- 새 task 가 ALB 가 보지 못하는 AZ 의 서브넷에 launch 되면 ALB 가 healthy 처리 자체를 못 함 → unhealthy → 회로차단기 → 자동 롤백
- 옛 task 가 "어쩌다 잘 돌던" 이유: 우연히 ALB-호환 AZ 에 떴기 때문
- **가용 영역 리밸런싱 ON** 이 켜져있으면 ECS 가 다음 배포에서 다른 AZ 로 일부러 분산시켜 상황 악화

**해결**:
1. ECS Service 의 네트워크 설정 → 서브넷을 **ALB 가 활성화한 2개 AZ 의 서브넷으로만 축소**
2. **가용 영역 리밸런싱 OFF** (2개 AZ 만 쓰므로 분산 의미 없음)
3. 강제 새 배포 트리거

```
변경 전: subnet 4개 (서로 다른 4 AZ, ALB 는 2 AZ 만 알아봄)
변경 후: subnet 2개 (ALB 활성 AZ 정확히 일치)
```

**대안**: ALB 에 가용 영역 추가도 가능하지만 ALB 가 인터넷-facing 인 경우 추가 서브넷이 Public 이어야 하는 등 조건 까다로워 ECS 쪽을 좁히는 것이 안전.

#### 함정 5: GitHub Actions `wait-for-minutes` < ECS 실제 배포 사이클
**증상**: ECS Deploy 단계가 `{"state":"TIMEOUT","reason":"Waiter has timed out"}` 로 fail 마킹. 그러나 ECS 콘솔에서는 같은 배포가 결국 ✅ 성공 — GH 시각에서는 실패, ECS 시각에서는 성공인 모순 상태.

**원인**: ECS Rolling Update 한 사이클 소요 시간 ~17분:

| 구간 | 소요 |
|---|---|
| 새 task launch + Spring Boot 부팅 | ~70초 |
| Health check grace period | 180초 |
| Target Group healthy 통과 (정상 임계값 2 × 30s 간격) | ~60초 |
| **옛 task drain** (Target Group `deregistration_delay` 기본 300초) | **5분** |
| ECS reconciliation 오버헤드 | ~30~60초 |
| **합계** | **약 17~18분** |

`amazon-ecs-deploy-task-definition@v2` 의 `wait-for-minutes: 15` 가 ECS 실제 사이클보다 짧아 GH 가 먼저 timeout. 본질은 **3개 계층 (GH Actions / ECS Circuit Breaker / Target Group drain) 의 시간 정책이 서로 어긋난 상태**.

**해결** (두 가지 같이):
1. `.github/workflows/deploy-ecs.yml` 의 `wait-for-minutes: 15 → 25`
2. ALB Target Group 의 `등록 취소 지연 (deregistration_delay)` 300 → **60초** (EC2 → 대상 그룹 → 속성 탭)
- 결과: drain 5분 → 1분, 전체 배포 ~12분, GH 25분 wait 안에 ECS 완료 신호 받고 정상 success 마킹

**부수 학습**:
- ALB 의 deregistration_delay 는 **시간 기반 정책** 이지 컨테이너 응답 모니터링이 아님. "in-flight 요청이 정말 끝났는지" 와 무관하게 시간만큼 무조건 draining 상태 유지
- 워크로드가 long-running 요청이 없는 REST API 면 60초로 줄여도 사용자 영향 거의 없음

---

다섯 함정 분류 요약:

| # | 분류 | 원인 |
|---|---|---|
| 1 | 네트워킹 호환성 | TG target type instance ↔ Fargate awsvpc |
| 2, 3 | IAM / SG 권한 누락 | V1 에서 암묵적이던 권한 경로 명시화 |
| 4 | AZ 토폴로지 미스매치 | ALB ↔ ECS Subnet AZ 불일치 |
| 5 | 시간 정책 계층화 미흡 | GH ↔ ECS ↔ TG 세 계층의 timeout 어긋남 |

운영 표준 패턴으로 옮기면서 V1 시절 "어쩌다 잘 돌던" 상태를 명시적 설정으로 다시 잡아야 했던 경험.

### 12.7 V1 보존 / V2 동시 운영

- V1 코드 (`docker-compose.prod.yml`, `rolling-deploy.sh`, V1 용 IAM Role) 는 git 에 보존 — 두 가지 배포 패턴 모두 다뤘다는 흔적
- V1 EC2 인스턴스는 정지 (필요 시 삭제). 컨테이너는 계속 떠 있어도 ALB Listener 가 forward 안 하므로 트래픽 0
- V1 의 옛 Target Group `workout-tracker-tg` 는 ALB 와 연결 끊긴 채로 남음 — 만약 V2 에서 문제 발생 시 ALB Listener forward 를 옛 TG 로 되돌려 즉시 롤백 가능 (수동 안전망)

---

## 부록. 자주 쓰는 명령어 모음

```bash
# 컨테이너 셸 진입
docker exec -it workout-tracker-backend sh

# 컨테이너 환경변수 확인 (시크릿 노출 주의)
docker exec workout-tracker-backend env | grep -v PASSWORD | grep -v SECRET

# 이미지 크기 확인
docker images | grep workout-tracker

# 사용 안 하는 이미지/캐시 정리 (디스크 가득 찼을 때)
docker system prune -a --volumes
```
