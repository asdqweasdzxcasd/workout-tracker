# ECS 오토스케일링 + 부하 테스트

workout-tracker 백엔드(ECS Fargate)에 Application Auto Scaling 을 적용하고 k6 로 부하 테스트한 구성.

## 구성 요약

| 항목 | 값 |
|---|---|
| Scalable target | ECS `workout-tracker-backend-svc`, **min 1 ~ max 4** |
| 정책 ① CPU | `ECSServiceAverageCPUUtilization` **60%** (target tracking) |
| 정책 ② ALB 요청수 | `ALBRequestCountPerTarget` **타겟당 200** (target tracking) |
| cooldown | scale-out 60s / scale-in 120s |
| 동작 | 두 정책 중 **하나라도 초과 → 스케일아웃**, 둘 다 낮아야 스케일인 |

두 지표를 함께 쓰는 이유: I/O 위주 요청은 CPU 가 낮아도 트래픽이 몰릴 수 있어 **요청수**가 먼저 반응하고, 연산 위주 요청은 **CPU** 가 먼저 반응한다. 서로 보완.

## 파일

- `autoscaling-setup.sh` — 오토스케일링 구성(register + 정책 2개). **`aws-stack.ps1 up` 이후 실행**. ALB/TG ARN 을 조회해 ResourceLabel 을 동적으로 만든다(ALB 는 up 마다 재생성되어 ARN 이 바뀌므로 하드코딩 불가).
- `auto-down.sh` — 매일 새벽 4시 크론. `aws-stack.ps1 down` **직전에 scalable target 을 deregister** 한다.
- `loadtest/health-rampup.js` — k6 부하 스크립트.

## ⚠️ 토글(up/down)과의 상호작용 — 중요

`aws-stack.ps1 down` 은 ECS desired 를 0 으로 내려 $0 로 만든다. 그런데 scalable target(min 1)이 남아 있으면 auto scaling 이 desired 를 1 로 되돌려 **$0 가 깨진다**. 그래서:

- **down 시**: `auto-down.sh` 가 down 직전에 `deregister-scalable-target` (정책도 함께 제거됨) → desired 0 유지.
- **up 시**: 스택이 뜬 뒤 **반드시 `./autoscaling-setup.sh` 를 실행**해야 오토스케일링이 복구된다. (aws-stack.ps1 은 토글 세션 소유라 여기서 자동 호출하지 않음 — up 후 수동 1회 실행.)

> 요약: `up` → `autoscaling-setup.sh` / `down`(자동·수동) → auto-down.sh 가 deregister.
> 수동으로 `aws-stack.ps1 down` 을 직접 돌릴 때도 먼저 deregister 하거나 auto-down.sh 를 쓸 것.

## 부하 테스트 실행

```bash
docker run --rm -i grafana/k6 run - < deploy/loadtest/health-rampup.js
# ECS 태스크 수 관찰 (다른 터미널)
watch -n5 'aws ecs describe-services --region ap-northeast-2 \
  --cluster workout-tracker-cluster --services workout-tracker-backend-svc \
  --query "services[0].{desired:desiredCount,running:runningCount}"'
```

## 시연 결과 (2026-07, talking point)

- 부하: 60 VU 램프업 → `actuator/health` 8분, **총 179,916 요청 / 평균 375 req/s / 실패율 0.00%** / p95 287ms.
- 스케일아웃: 요청수가 임계 초과 후 **약 5분 뒤 desired 1→4** 결정(ALB 알람 3분 연속 확인), **약 1분 만에 4태스크 가동**.
- 스케일인: 스케일아웃보다 보수적(약 15분 저부하 확인 후 축소) — 트래픽 재상승 대비한 target tracking 의 의도적 비대칭.
