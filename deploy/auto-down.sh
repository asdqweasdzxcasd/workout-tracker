#!/usr/bin/env bash
# workout-tracker AWS 스택 자동 down (크론 전용)
# 매일 새벽 4시에 실행 — 스택이 켜져 있으면 $0로 내린다.
# aws-stack.ps1 down 은 idempotent 하므로 이미 꺼져 있으면 각 리소스를
# "already absent" 로 스킵한다. 즉 "켜져 있으면 끄고, 꺼져 있으면 무동작".
#
# 크론 환경은 PATH/HOME 이 최소라 명시적으로 세팅한다.
#   - pwsh:  /snap/bin/pwsh
#   - aws :  /home/asd/.local/bin/aws (스크립트가 PATH 에서 찾음)
#   - HOME:  ~/.aws 자격증명 로드용
set -uo pipefail

export HOME=/home/asd
export PATH="/home/asd/.local/bin:/snap/bin:/usr/bin:/bin"

DIR="/home/asd/dev/workout-tracker/deploy"
LOG="$DIR/auto-down.log"
STAMP() { date '+%F %T %Z'; }

echo "===== $(STAMP) auto-down 시작 =====" >> "$LOG"

# 오토스케일링 scalable target 해제 — min 1 이 down 의 desired 0 을 되돌리는 것 방지.
# (up 이후엔 autoscaling-setup.sh 로 다시 구성한다.) 이미 없으면 무시.
aws application-autoscaling deregister-scalable-target --region ap-northeast-2 \
  --service-namespace ecs \
  --resource-id service/workout-tracker-cluster/workout-tracker-backend-svc \
  --scalable-dimension ecs:service:DesiredCount >> "$LOG" 2>&1 \
  && echo "$(STAMP) scalable target deregister 완료" >> "$LOG" \
  || echo "$(STAMP) scalable target 없음/해제 스킵" >> "$LOG"

pwsh -File "$DIR/aws-stack.ps1" down >> "$LOG" 2>&1
rc=$?
echo "===== $(STAMP) auto-down 종료 (exit=$rc) =====" >> "$LOG"
exit $rc
