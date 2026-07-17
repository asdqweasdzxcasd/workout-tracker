# AWS 스택 스냅샷 (재생성용)

> workout-tracker AWS 인프라를 `$0 ↔ 가동` 으로 올렸다 내리기 위한 설정 캡처.
> 캡처 시점: 2026-07-01 / 리전: ap-northeast-2 / 계정: 610156626396

## 영구 유지 (삭제 안 함 — 거의 $0)
| 리소스 | 식별자 | 비고 |
|--------|--------|------|
| VPC | `vpc-0c7f8a8dd47a97ce7` | 무료 |
| 서브넷 | `subnet-00ef62d78c2ea01fd`, `subnet-0267d72adf5e66c78` | ALB·Redis 공용 |
| ALB SG | `sg-067e18a5fc64e5b5e` | |
| Redis SG | `sg-0fbd95ae3529081b0` | |
| 타겟그룹 | `workout-tracker-tg-fargate` (HTTP 8080, ip타입, health `/actuator/health`) | ALB 지워도 남김 → ECS 재바인딩 불필요 |
| Redis 서브넷그룹 | `workout-tracker-cache-subnets` | 레플그룹 지워도 남음 |
| Redis 파라미터그룹 | `default.valkey9` | |
| ECR / IAM 역할 / S3 / SSM | `workout-tracker-backend` / `WorkoutTrackerGithubDeployRole` 등 / `silee-workout-tracker-photos` / `/workout-tracker/*` | CI·태스크가 의존 → 영구 |

## stop/start (삭제 안 함, 데이터 보존)
| 리소스 | 식별자 | 끄기 | 켜기 |
|--------|--------|------|------|
| ECS 서비스 | 클러스터 `workout-tracker-cluster`, 서비스 `workout-tracker-backend-svc` | desired 0 | desired 2(또는 1) |
| RDS | `workout-tracker-db` (db.t4g.micro, postgres) | stop-db-instance | start-db-instance + wait |

## 삭제 / 재생성 (stop 불가 = 이것만 진짜 삭제)
### ALB `workout-tracker-alb`
- scheme: internet-facing / type: application / ipv4
- subnets: 위 2개 / security-groups: `sg-067e18a5fc64e5b5e` (인바운드 80,443 영구 유지)
- listener 1: **HTTP:80** → forward 타겟그룹 `workout-tracker-tg-fargate`
- listener 2: **HTTPS:443** → SSL policy `ELBSecurityPolicy-2016-08` / ACM cert `arn:aws:acm:ap-northeast-2:610156626396:certificate/fd5e4f68-a531-4945-bd3a-1ccd863ae153` (`workout-api.eeiu.net`, ISSUED, 영구) → forward 같은 타겟그룹
- 재생성 후: 새 DNS 이름 → Cloudflare CNAME 갱신(스크립트 자동). Vercel은 `https://workout-api.eeiu.net` 고정이라 안 건드림
- (HTTPS는 2026-07-04 옆 세션이 추가 — `aws-stack.ps1` up 로직이 443 리스너 재생성 포함)

### Redis 레플리케이션 그룹 `workout-tracker-redis`
- node: cache.t4g.micro / engine: valkey 9.0.0 / 단일노드(failover·multiAZ disabled)
- subnet-group: `workout-tracker-cache-subnets` / param-group: `default.valkey9`
- security-group: `sg-0fbd95ae3529081b0` / port: 6379
- **transit-encryption: ON / at-rest-encryption: ON / auth-token: 없음**
- 재생성 후: 새 primary endpoint → SSM `/workout-tracker/REDIS_HOST` 갱신 필요
- 데이터(refresh 토큰)는 소실돼도 무방

## 접속정보 (SSM Parameter Store, 재생성 시 갱신 대상)
- `/workout-tracker/REDIS_HOST` ← 재생성마다 갱신 (현재: `master.workout-tracker-redis.uxiiag.apn2.cache.amazonaws.com`)
- `/workout-tracker/REDIS_PORT` = 6379 (고정)
- `/workout-tracker/DB_URL` = RDS 엔드포인트 (stop/start라 **안 바뀜**, 갱신 불필요)
- 그 외: DB_USERNAME, DB_PASSWORD, JWT_ACCESS_SECRET, JWT_REFRESH_SECRET, JWT_SECRET

## 프론트 → 백엔드 연결
- 프론트: Vercel. BFF 프록시(`/api/proxy/*`)가 서버사이드 env `EC2_API_URL` 로 백엔드 호출.
- ALB 재생성 시 이 값 갱신 필요. (권장: Cloudflare 고정 도메인으로 우회 → Vercel 안 건드림)

## GitHub Actions
- `deploy-ecs.yml`: 수동 트리거. 리소스를 이름으로 참조 → 같은 이름 재생성이면 **수정 불필요**. ECS 서비스/ECR 영구 유지가 전제.
- `e2e.yml`, `backend-test.yml`: 러너 내부 컨테이너로 테스트 → AWS 스택과 무관.
