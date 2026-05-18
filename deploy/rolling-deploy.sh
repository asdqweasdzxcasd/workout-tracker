#!/bin/bash
# =====================================================
# workout-tracker Rolling 배포 스크립트
#
# 흐름:
#   1. git pull (최신 코드)
#   2. 새 이미지 빌드
#   3. Blue 컨테이너 deregister (ALB Target Group)
#   4. Blue 컨테이너 새 이미지로 재시작
#   5. Blue healthy 대기
#   6. Blue 다시 register
#   7. Green 도 동일하게 반복
#
# 사용:
#   chmod +x rolling-deploy.sh
#   ./rolling-deploy.sh
#
# 요구사항:
#   - AWS CLI 설치 + EC2 IAM Role 에 elbv2 권한 (또는 awscli credentials)
#   - 환경변수 또는 아래 설정 변수 채우기
# =====================================================

set -euo pipefail

# ----------------------------------------------------
# 설정 (운영에 맞게 조정)
# ----------------------------------------------------
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
TARGET_GROUP_NAME="${TARGET_GROUP_NAME:-workout-tracker-tg}"
EC2_INSTANCE_ID="${EC2_INSTANCE_ID:-i-0094f2b5b59d08ece}"
HEALTH_CHECK_TIMEOUT_SEC=180   # 컨테이너 부팅 + healthy 까지 최대 대기 시간

# ----------------------------------------------------
# 함수
# ----------------------------------------------------
log() {
  echo "[$(date +'%H:%M:%S')] $*"
}

# Target Group ARN 조회
get_tg_arn() {
  aws elbv2 describe-target-groups \
    --names "$TARGET_GROUP_NAME" \
    --region "$AWS_REGION" \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text
}

# 특정 포트의 컨테이너를 Target Group 에서 분리
deregister() {
  local port=$1
  log "ALB Target Group 에서 분리: port=$port"
  aws elbv2 deregister-targets \
    --target-group-arn "$TG_ARN" \
    --targets "Id=$EC2_INSTANCE_ID,Port=$port" \
    --region "$AWS_REGION"
}

# Target Group 에 등록
register() {
  local port=$1
  log "ALB Target Group 에 등록: port=$port"
  aws elbv2 register-targets \
    --target-group-arn "$TG_ARN" \
    --targets "Id=$EC2_INSTANCE_ID,Port=$port" \
    --region "$AWS_REGION"
}

# Target healthy 가 될 때까지 대기
wait_healthy() {
  local port=$1
  local elapsed=0
  log "healthy 대기 중: port=$port"
  while [ $elapsed -lt $HEALTH_CHECK_TIMEOUT_SEC ]; do
    local state
    state=$(aws elbv2 describe-target-health \
      --target-group-arn "$TG_ARN" \
      --targets "Id=$EC2_INSTANCE_ID,Port=$port" \
      --region "$AWS_REGION" \
      --query 'TargetHealthDescriptions[0].TargetHealth.State' \
      --output text 2>/dev/null || echo "unknown")
    if [ "$state" = "healthy" ]; then
      log "  → healthy (port=$port, 소요 ${elapsed}s)"
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    log "  → state=$state, 대기 중... (${elapsed}s)"
  done
  log "❌ healthy 대기 타임아웃 (port=$port)"
  return 1
}

# 컨테이너 재시작 + 헬스체크 대기 (호스트 헬스체크 자체 검증)
restart_container() {
  local service=$1
  local port=$2
  log "컨테이너 재시작: $service (port=$port)"
  docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate --build "$service"
  log "호스트 헬스체크 대기: http://localhost:$port/actuator/health"
  local elapsed=0
  while [ $elapsed -lt $HEALTH_CHECK_TIMEOUT_SEC ]; do
    if curl -fs "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
      log "  → 컨테이너 healthy (port=$port, 소요 ${elapsed}s)"
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  log "❌ 컨테이너 헬스체크 타임아웃 (port=$port)"
  return 1
}

# ----------------------------------------------------
# 메인
# ----------------------------------------------------
log "=== Rolling 배포 시작 ==="
TG_ARN=$(get_tg_arn)
log "Target Group ARN: $TG_ARN"

# git pull (최신 코드)
log "git pull"
cd "$(dirname "$0")/.."
git pull
cd deploy

# --- Blue 교체 ---
log "--- Blue 교체 ---"
deregister 8080
log "drain 대기 30s (in-flight 요청 처리)"
sleep 30
restart_container workout-tracker-backend-blue 8080
register 8080
wait_healthy 8080

# --- Green 교체 ---
log "--- Green 교체 ---"
deregister 8081
log "drain 대기 30s"
sleep 30
restart_container workout-tracker-backend-green 8081
register 8081
wait_healthy 8081

log "=== Rolling 배포 완료 ==="
