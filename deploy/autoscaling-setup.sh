#!/usr/bin/env bash
# workout-tracker ECS 오토스케일링 구성 (aws-stack.ps1 up 이후 실행)
#
# 배경: aws-stack.ps1 down 은 ECS desired 를 0 으로 내려 $0 로 만든다. 그런데
# Application Auto Scaling scalable target(min 1)이 남아 있으면 auto scaling 이
# desired 를 다시 1 로 되돌려 $0 가 깨진다. 그래서 auto-down.sh 가 down 직전에
# scalable target 을 deregister 하고(정책도 함께 사라짐), up 이후 이 스크립트로
# 다시 구성한다.
#
# ALB 는 up 때마다 재생성되어 ARN(=ResourceLabel suffix)이 바뀌므로, ALB/TG ARN 을
# 하드코딩하지 않고 매번 조회해서 ResourceLabel 을 만든다.
#
# 사용: ./autoscaling-setup.sh   (aws-stack.ps1 up 으로 스택이 뜬 뒤)
set -uo pipefail
export PATH="/home/asd/.local/bin:/usr/bin:/bin"

REGION=ap-northeast-2
CLUSTER=workout-tracker-cluster
SERVICE=workout-tracker-backend-svc
RES="service/${CLUSTER}/${SERVICE}"
DIM=ecs:service:DesiredCount
MIN=1
MAX=4

echo "== ALB/TG ARN 조회 → ResourceLabel 구성 =="
ALB_ARN=$(aws elbv2 describe-load-balancers --region $REGION --names workout-tracker-alb \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)
TG_ARN=$(aws elbv2 describe-target-groups --region $REGION --names workout-tracker-tg-fargate \
  --query 'TargetGroups[0].TargetGroupArn' --output text)
ALB_SUFFIX=$(echo "$ALB_ARN" | sed -E 's#.*:loadbalancer/(app/[^ ]+)#\1#')
TG_SUFFIX=$(echo "$TG_ARN" | sed -E 's#.*:(targetgroup/[^ ]+)#\1#')
RL="${ALB_SUFFIX}/${TG_SUFFIX}"
echo "  ResourceLabel: $RL"

echo "== ① scalable target 등록 (min $MIN, max $MAX) =="
aws application-autoscaling register-scalable-target --region $REGION \
  --service-namespace ecs --resource-id "$RES" --scalable-dimension $DIM \
  --min-capacity $MIN --max-capacity $MAX

echo "== ② CPU 정책 (ECSServiceAverageCPUUtilization 60%) =="
aws application-autoscaling put-scaling-policy --region $REGION \
  --service-namespace ecs --resource-id "$RES" --scalable-dimension $DIM \
  --policy-name workout-cpu-60 --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 60.0,
    "PredefinedMetricSpecification": {"PredefinedMetricType": "ECSServiceAverageCPUUtilization"},
    "ScaleInCooldown": 120, "ScaleOutCooldown": 60
  }' >/dev/null && echo "  ok"

echo "== ③ ALB 요청수 정책 (ALBRequestCountPerTarget 타겟당 200) =="
aws application-autoscaling put-scaling-policy --region $REGION \
  --service-namespace ecs --resource-id "$RES" --scalable-dimension $DIM \
  --policy-name workout-alb-reqcount-200 --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration "{
    \"TargetValue\": 200.0,
    \"PredefinedMetricSpecification\": {
      \"PredefinedMetricType\": \"ALBRequestCountPerTarget\",
      \"ResourceLabel\": \"$RL\"
    },
    \"ScaleInCooldown\": 120, \"ScaleOutCooldown\": 60
  }" >/dev/null && echo "  ok"

echo "== 완료: 오토스케일링 구성됨 (min $MIN ~ max $MAX, CPU 60% + ALB req/target 200) =="
