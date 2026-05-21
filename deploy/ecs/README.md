# ECS Fargate 마이그레이션 가이드

기존 EC2 + Docker Compose Blue/Green 구조를 ECS Fargate 로 옮기는 절차.

## 진행 순서 요약

| Phase | 작업 | 도구 | 시간 |
|---|---|---|---|
| 1 | ECR 리포지토리 생성 | AWS Console | 5분 |
| 2 | SSM Parameter Store 에 시크릿 등록 | AWS Console / CLI | 10분 |
| 3 | IAM Role 3종 생성 (Task Execution / Task / GitHub OIDC) | AWS Console | 15분 |
| 4 | CloudWatch Log Group 생성 (선택, 자동 생성 가능) | AWS Console | 2분 |
| 5 | ALB Target Group 재구성 (target type = `ip`) | AWS Console | 10분 |
| 6 | ECS Cluster + Service 생성 | AWS Console | 20분 |
| 7 | GitHub Variables 설정 | GitHub UI | 5분 |
| 8 | 첫 배포 (`main` push) | 자동 | 5~10분 |

총 1~1.5시간.

---

## Phase 1. ECR 리포지토리 생성

AWS Console → ECR → Private repositories → Create repository

- 이름: `workout-tracker-backend`
- 리전: `ap-northeast-2`
- Image scanning: Enhanced (선택, 무료 한도 내)
- Encryption: AES-256

생성 후 URI 예시: `<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/workout-tracker-backend`

---

## Phase 2. SSM Parameter Store 에 시크릿 등록

ECS Task 가 환경변수로 받을 시크릿을 SSM Parameter Store 에 SecureString 으로 저장.

| Parameter 이름 | 타입 | 값 |
|---|---|---|
| `/workout-tracker/DB_URL` | String | `jdbc:postgresql://<RDS-ENDPOINT>:5432/workout_tracker` |
| `/workout-tracker/DB_USERNAME` | String | `workout` |
| `/workout-tracker/DB_PASSWORD` | **SecureString** | RDS 마스터 비밀번호 |
| `/workout-tracker/JWT_SECRET` | **SecureString** | 64자 이상 랜덤 |

### Console 단계
1. Systems Manager → Parameter Store → Create parameter
2. 위 표의 각 항목에 대해 반복
3. SecureString 은 KMS key = `alias/aws/ssm` (기본) 사용

### AWS CLI 로 (대안)
```bash
aws ssm put-parameter --name /workout-tracker/DB_URL \
  --type String --value "jdbc:postgresql://<RDS-ENDPOINT>:5432/workout_tracker"
aws ssm put-parameter --name /workout-tracker/DB_USERNAME \
  --type String --value "workout"
aws ssm put-parameter --name /workout-tracker/DB_PASSWORD \
  --type SecureString --value "<실제 비밀번호>"
aws ssm put-parameter --name /workout-tracker/JWT_SECRET \
  --type SecureString --value "<64자 이상 랜덤>"
```

---

## Phase 3. IAM Role 3종 생성

### 3.1 Task Execution Role
ECS 가 ECR 에서 이미지 pull + CloudWatch 로그 출력 + SSM Parameter 읽기.

**Console**: IAM → Roles → Create role
- Trusted entity type: AWS service
- Use case: Elastic Container Service Task
- 권한 정책: 우선 `AmazonECSTaskExecutionRolePolicy` (managed) 부착 (ECR + CloudWatch 기본)
- 역할 이름: `WorkoutTrackerEcsTaskExecutionRole`

생성 후 **인라인 정책 추가** (SSM Parameter 읽기 권한):
- `iam/task-execution-role-inline-policy.json` 내용 그대로 붙여넣기
- 정책 이름: `workout-tracker-ssm-read`

### 3.2 Task Role
애플리케이션 (Spring Boot) 이 사용할 권한. 현재는 S3 만.

**Console**: IAM → Roles → Create role
- Trusted entity type: Custom trust policy
- Trust policy: `iam/trust-policy-ecs-tasks.json` 내용 붙여넣기
- 권한 정책: 생성 후 인라인 정책 추가
  - `iam/task-role-inline-policy.json` 의 `__S3_BUCKET__` 을 실제 버킷명으로 치환 후 붙여넣기
  - 정책 이름: `workout-tracker-s3`
- 역할 이름: `WorkoutTrackerEcsTaskRole`

### 3.3 GitHub Deploy Role (OIDC)
GitHub Actions 가 임시 자격증명으로 ECR push + ECS update 하도록.

**먼저 OIDC Provider 등록 (계정당 1회):**
- IAM → Identity providers → Add provider
- Provider type: OpenID Connect
- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

**Role 생성:**
- IAM → Roles → Create role
- Trusted entity type: Web identity
- Identity provider: 방금 만든 `token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`
- GitHub organization: `asdqweasdzxcasd`
- GitHub repository: `workout-tracker`
- Branch (선택): `main`
- 권한 정책: 생성 후 인라인 정책 추가
  - `iam/github-deploy-role-inline-policy.json` 의 `__ACCOUNT_ID__` 치환 후 붙여넣기
  - 정책 이름: `workout-tracker-deploy`
- 역할 이름: `WorkoutTrackerGithubDeployRole`

> Console 의 Web identity 마법사가 trust policy 를 자동 생성한다. 본 디렉토리의 `iam/github-oidc-trust-policy.json` 는 수동 생성 시 참고용.

---

## Phase 4. CloudWatch Log Group (선택)

Task Definition 의 `awslogs-create-group: true` 옵션으로 자동 생성된다. 미리 만들고 싶으면:

- CloudWatch → Log groups → Create log group
- 이름: `/ecs/workout-tracker-backend`
- Retention: 7 days (비용 절감, 학습 프로젝트 기준)

---

## Phase 5. ALB Target Group 재구성

Fargate task 는 `awsvpc` 네트워크 모드라 Target Group 의 **target type = `ip`** 이어야 한다. 현재 EC2 용 Target Group (`instance` 타입) 은 호환 안 됨.

### 새 Target Group 생성
- EC2 → Target Groups → Create target group
- Target type: **`IP addresses`** ⭐ (instance 아님)
- Target group name: `workout-tracker-tg-fargate`
- Protocol: HTTP, Port: 8080
- VPC: 기존과 동일
- Protocol version: HTTP1
- Health check path: `/actuator/health`
- Healthy threshold: 2, Interval: 30s, Timeout: 5s
- **Register targets 단계는 비워둠** (ECS Service 가 자동 등록)
- Create

### ALB Listener 규칙 변경
- 기존 ALB 의 80 Listener → 규칙 편집
- Default action 의 Forward to → 새 `workout-tracker-tg-fargate` 로 변경
- 기존 `workout-tracker-tg` 는 일단 보존 (롤백 대비, 추후 삭제)

---

## Phase 6. ECS Cluster + Service 생성

### 6.1 Cluster 생성
- ECS → Clusters → Create cluster
- Cluster name: `workout-tracker-cluster`
- Infrastructure: **AWS Fargate (serverless)**
- 나머지 기본값

### 6.2 Task Definition 등록 (수동 1회, 이후 GitHub Actions 가 자동)
- ECS → Task definitions → Create new task definition
- Launch type: Fargate
- Task definition family: `workout-tracker-backend`
- OS, Architecture: Linux/X86_64
- Task CPU: 0.5 vCPU, Memory: 1GB
- Task role: `WorkoutTrackerEcsTaskRole`
- Task execution role: `WorkoutTrackerEcsTaskExecutionRole`
- Container 추가:
  - Name: `workout-tracker-backend`
  - Image URI: `<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/workout-tracker-backend:latest`
  - Port: 8080/tcp
  - Environment / Secrets: task-definition.json 그대로
  - Health check: `wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1`
  - Log: awslogs → `/ecs/workout-tracker-backend`

> 또는 본 디렉토리의 `task-definition.json` 을 placeholder 치환 후 그대로 등록 (AWS CLI):
> ```bash
> sed -e "s|__ACCOUNT_ID__|<your-id>|g" \
>     -e "s|__AWS_REGION__|ap-northeast-2|g" \
>     -e "s|__IMAGE_TAG__|latest|g" \
>     -e "s|__S3_BUCKET__|<your-bucket>|g" \
>   task-definition.json > /tmp/td.json
> aws ecs register-task-definition --cli-input-json file:///tmp/td.json
> ```

### 6.3 Service 생성
- Cluster `workout-tracker-cluster` → Services → Create
- Compute configuration: Capacity provider strategy = `FARGATE` (가중치 100, base 1)
- Application type: Service
- Task family: `workout-tracker-backend` (최신 revision)
- Service name: `workout-tracker-backend-svc`
- Service type: Replica
- **Desired tasks: 2** (Blue/Green 흉내내며 자동 자가 치유)
- Deployment options:
  - Deployment type: Rolling update
  - Min running tasks %: 100 (배포 중 가용성 보장)
  - Max running tasks %: 200 (배포 중 한 task 더 띄움)
- Networking:
  - VPC: 기존
  - Subnets: Private 권장 (인터넷 직접 안 보임), 다만 Fargate 가 ECR/SSM 접근하려면 NAT Gateway 필요. **NAT 비용 부담 시 Public Subnet + Public IP 자동 할당** 선택 가능 (학습 OK)
  - Security Group: 새 `ecs-tasks-sg` 생성 — inbound TCP 8080 ← ALB SG (`alb-sg`) 만 허용
  - Auto-assign public IP: Public Subnet 사용 시 Enable
- Load balancing:
  - Application Load Balancer
  - 기존 `workout-tracker-alb` 선택
  - Listener: HTTP 80, Forward to → `workout-tracker-tg-fargate`
- Service auto scaling: 일단 Off (학습 OK, 나중에 추가)

생성 후 약 2~3분 안에 Task 2개가 RUNNING 상태가 되고 Target Group 에 healthy 로 등록된다.

---

## Phase 7. GitHub Variables 설정

GitHub Settings → Secrets and variables → Actions → **Variables** 탭

| 이름 | 값 |
|---|---|
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_ACCOUNT_ID` | (12-digit) |
| `ECR_REPOSITORY` | `workout-tracker-backend` |
| `ECS_CLUSTER` | `workout-tracker-cluster` |
| `ECS_SERVICE` | `workout-tracker-backend-svc` |
| `S3_BUCKET` | (실제 S3 버킷명) |
| `DEPLOY_ROLE_ARN` | `arn:aws:iam::<ACCOUNT_ID>:role/WorkoutTrackerGithubDeployRole` |

Secrets 가 아닌 **Variables** 임 주의 (시크릿 아님, 이름/ARN 만 노출 OK).

---

## Phase 8. 첫 배포

```bash
git commit --allow-empty -m "ECS 첫 배포 트리거"
git push origin main
```

또는 GitHub Actions 탭 → "Deploy ECS" workflow → Run workflow 수동 실행.

### 모니터링
- GitHub Actions 실행 로그
- ECS Console → Cluster → Service → Events / Tasks 탭
- CloudWatch Logs `/ecs/workout-tracker-backend` 로그 스트림

### 검증
```bash
curl https://<ALB-DNS>/actuator/health
# { "status": "UP" } 응답
```

기존 Vercel 도메인 (`workout-tracker-ten-zeta.vercel.app`) 도 그대로 동작해야 함 — BFF 가 같은 ALB 를 가리키므로 변경 불필요.

---

## 비용 정지 (학습/시연 후)

Fargate 는 task 가 떠 있는 시간만 과금. 정지 방법:

```bash
aws ecs update-service \
  --cluster workout-tracker-cluster \
  --service workout-tracker-backend-svc \
  --desired-count 0
```

ALB / RDS / ECR 은 별도. ALB 정지 = 삭제 또는 그대로 두기.

다시 띄울 때:
```bash
aws ecs update-service \
  --cluster workout-tracker-cluster \
  --service workout-tracker-backend-svc \
  --desired-count 2
```

---

## 옛 EC2 Blue/Green 처리

이번 마이그레이션 후에도 `deploy/docker-compose.prod.yml` / `deploy/rolling-deploy.sh` 는 코드로 보존. 단 실제 EC2 인스턴스는 중지 (필요 시 종료) 가능.

> "ECS 전환 전 단순한 EC2 + docker compose + 직접 Rolling 스크립트 패턴도 검토/구현했다" 는 마이그레이션 흔적이 코드에 남아있어, 두 패턴 모두 다뤄봤다는 talking point 가 됨.

---

## 트러블슈팅

### 실전 사례: 마이그레이션 중 만난 3가지 함정

본 프로젝트 V1→V2 진행 중 실제로 만난 함정. ECS Deployment Circuit Breaker 가 두 번 발동.

#### ① ALB Target Group `instance` ↔ Fargate `awsvpc` 호환 안 됨
**증상**: ECS Service 생성 마법사에서 옛 instance-type TG 선택 시 비활성 + "Fargate awsvpc 모드와 호환 안 됨" 경고.

**원인**: Fargate 는 task 마다 ENI 부여 (awsvpc 모드) → ALB Target Group 의 target type 이 `ip` 여야 함. 옛 V1 TG 는 EC2 instance ID 등록용이라 부적합.

**해결**: 새 TG `workout-tracker-tg-fargate` (target type=ip) 생성 + ALB Listener default forward 를 새 TG 로 변경.

#### ② `logs:CreateLogGroup` 권한 부재
**증상**: 첫 배포 즉시 task 가 `STOPPED (TaskFailedToStart)`. 이벤트 메시지:
```
ResourceInitializationError: failed to validate logger args:
... AccessDeniedException ... is not authorized to perform: logs:CreateLogGroup
```

**원인**: 관리형 정책 `AmazonECSTaskExecutionRolePolicy` 는 PutLogEvents 만 허용. Task Definition 의 `awslogs-create-group: "true"` 옵션 사용 시 권한 부족.

**해결**: Task Execution Role 의 인라인 정책에 다음 Statement 추가:
```json
{
  "Sid": "CloudWatchLogsCreateGroup",
  "Effect": "Allow",
  "Action": ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"],
  "Resource": "arn:aws:logs:*:*:log-group:/ecs/workout-tracker-*:*"
}
```

#### ③ RDS Security Group 이 새 `ecs-tasks-sg` 차단
**증상**: ②번 해결 후 컨테이너 시작은 됐으나 Spring Boot 부팅 도중 DB 연결 timeout.
```
Caused by: java.net.SocketTimeoutException: Connect timed out
at org.postgresql.core.PGStream.createSocket(...)
```

**원인**: RDS Security Group 의 인바운드가 V1 시절의 `web-sg` (EC2) 만 허용. 새 `ecs-tasks-sg` 는 RDS 가 모름.

**해결**: RDS SG 인바운드 규칙 추가 — `PostgreSQL 5432 ← ecs-tasks-sg`.

세 사례 모두 V1 에서 V2 로 옮기면서 "암묵적으로 통과되던 권한 경로"를 명시적으로 다시 부여해야 했던 케이스. SG 체인과 IAM 권한 분리를 점검 항목으로 가져갈 가치.

---

### 일반 트러블슈팅

#### Task 가 PROVISIONING 에서 멈춤
- Subnet 가 private 인데 NAT Gateway 없음 → ECR pull 실패
- 해결: Subnet 을 Public 으로 + Auto-assign public IP

#### Task 가 STOPPED with exit code 1
- CloudWatch Logs `/ecs/workout-tracker-backend` 에서 부팅 로그 확인
- 가장 흔한 원인: SSM Parameter 읽기 실패 → Task Execution Role 의 SSM 권한 점검

#### Target Group 이 unhealthy
- ALB SG → ECS tasks SG inbound 8080 허용 확인
- Task Definition health check 가 `localhost:8080/actuator/health` 200 반환 확인 (`docker exec` 으로 직접 확인 어려움 — CloudWatch Logs 활용)
- Service 의 `상태 검사 유예 기간` 이 너무 짧음 (Spring Boot 부팅 60초+ 고려해 60 이상 권장)

### ECR push 권한 에러
- DEPLOY_ROLE_ARN 의 trust policy `sub` 가 `repo:asdqweasdzxcasd/workout-tracker:ref:refs/heads/main` 와 정확히 일치하는지
- 다른 브랜치/PR 에서 trigger 하려면 sub 조건 완화 필요
