# 아키텍처 다이어그램 소스

## 파일

| 파일 | 용도 | 편집 도구 |
|---|---|---|
| `architecture.drawio` | 운영 아키텍처 (AWS 아이콘 사용) | https://app.diagrams.net |

## 편집 + 이미지 export 방법

1. https://app.diagrams.net 접속 (가입 불필요)
2. `Open Existing Diagram` → `Device` → `architecture.drawio` 선택
3. AWS 아이콘이 안 보이면: 좌측 패널 하단 `More Shapes...` → **AWS19** 카테고리 체크
4. 편집 후 `File → Save` (브라우저가 같은 .drawio 로 저장)
5. PNG export: `File → Export as → PNG`
   - Border: 10px
   - Background: White (불투명)
   - Zoom: 200% (선명도)
   - 파일명: `architecture.png` → 같은 폴더에 저장
6. README 에서 이미지 임베드:

```markdown
![Architecture](docs/diagrams/architecture.png)
```

## 다이어그램에 포함된 핵심 talking point

- **3-tier 분리**: Browser ─ Vercel (Next.js + BFF) ─ AWS (백엔드)
- **Vercel BFF 가 HTTPS→HTTP forward**: Mixed Content 회피 + 백엔드 주소 은닉
- **ALB Blue/Green** 두 컨테이너 Target Group 등록: Rolling 무중단 배포의 기반
- **EC2 IAM Role (IMDSv2)**: AccessKey 환경변수 0개, 임시 자격증명 자동 회전
- **presigned URL 대각선 화살표**: 대용량 이미지가 백엔드 메모리/대역폭 우회
- **SG 분리** (`alb-sg` → `web-sg` → `db-sg`): 인터넷 → ALB → EC2 → RDS 순으로 좁힘

## Mermaid 다이어그램 (소스 코드)

본 폴더의 `.drawio` 외에도 `README.md`, `deploy/DEPLOY.md`, `docs/design.md` 안에 Mermaid 다이어그램 7개가 인라인으로 정의되어 있다 (GitHub 가 자동 렌더). 텍스트 기반이라 git 으로 버전 관리 + diff 가능.

| 위치 | 다이어그램 |
|---|---|
| `README.md` | 운영 아키텍처 flowchart, 데이터 모델 ERD |
| `deploy/DEPLOY.md` | Rolling 배포 시퀀스, CI 파이프라인 flowchart, 5 라운드 timeline, hydration race 시퀀스 |
| `docs/design.md` | JWT 인증 시퀀스, S3 Presigned URL 시퀀스 |
