# workout-tracker 배포 가이드 (Phase 6.2)

대상 환경: AWS EC2 t3.small (Amazon Linux 2023, Docker + Docker Compose 설치 완료)
백엔드: Spring Boot 3.3.5 (Java 17) on Docker
DB: AWS RDS PostgreSQL 16 (별도 인스턴스)
스토리지: AWS S3 (`silee-workout-tracker-photos`)

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

채워야 할 값:

| 항목 | 값 | 비고 |
|---|---|---|
| `DB_PASSWORD` | RDS 마스터 비밀번호 | RDS 생성 시 설정한 값 |
| `JWT_SECRET` | 64자 이상 랜덤 문자열 | `openssl rand -base64 64` 로 생성 |
| `AWS_ACCESS_KEY_ID` | IAM 사용자 AccessKey | `workout-tracker-dev` IAM 사용자 발급분 |
| `AWS_SECRET_ACCESS_KEY` | IAM 사용자 SecretKey | 동일 |

> Phase 6.3 에서 EC2 IAM Role 부착으로 전환하면 AccessKey/SecretKey 두 줄 삭제 가능.

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

```bash
cd ~/projects/workout-tracker
git pull

cd deploy
docker compose --env-file .env -f docker-compose.prod.yml up -d --build

# 빌드 캐시 활용으로 보통 2~3분 내 완료
# (의존성 변경 없으면 Gradle 캐시 레이어 재사용)
```

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
psql -h workout-tracker-db.cxmy462ieik7.ap-northeast-2.rds.amazonaws.com \
     -U workout -d workout_tracker
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

- `.env` 의 `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` 정확한지
- IAM 사용자에 `s3:PutObject`, `s3:GetObject` 권한 있는지 (`silee-workout-tracker-photos/*`)
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

## 8. Phase 6.3 사전 결정 사항

다음 단계 (IAM Role 부착) 전 결정 필요:

1. EC2 인스턴스 프로파일 생성 정책: 신규 생성 vs 기존 사용
2. S3 권한 범위: `s3:PutObject` + `s3:GetObject` 만 vs `s3:ListBucket` 도 부여
3. AccessKey 환경변수 제거 시점: 검증 완료 후 vs 즉시

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
