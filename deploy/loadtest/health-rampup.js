// workout-tracker 부하 테스트 (k6) — ECS 오토스케일링 스케일아웃 유도용
//
// 목적: ALBRequestCountPerTarget(타겟당 200) 정책을 초과시켜 ECS 태스크가
//       1 → 4 로 스케일아웃 하는지 관찰. 이후 부하를 빼면 스케일인 관찰.
//
// 실행 (Docker, 설치 불필요):
//   docker run --rm -i grafana/k6 run - < deploy/loadtest/health-rampup.js
//
// 대상은 인증 불필요한 actuator/health — 순수하게 요청량(ALB 지표)으로 스케일 유도.
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  // 램프업 → 유지(스케일아웃 관찰) → 램프다운(스케일인 관찰)
  stages: [
    { duration: '2m', target: 60 },  // 0 → 60 VU 로 증가
    { duration: '4m', target: 60 },  // 60 VU 유지 (이 구간에 스케일아웃 발생 기대)
    { duration: '2m', target: 0 },   // 부하 제거 (스케일인 관찰)
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],       // 실패율 5% 미만
    http_req_duration: ['p(95)<2000'],    // p95 2초 미만
  },
};

const BASE = __ENV.TARGET_URL || 'https://workout-api.eeiu.net';

export default function () {
  const res = http.get(`${BASE}/actuator/health`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
