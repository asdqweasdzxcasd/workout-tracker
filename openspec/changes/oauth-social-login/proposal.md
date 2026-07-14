## Why

현재 workout-tracker는 자체 이메일/비밀번호 가입(D.1 JWT + D.2 이메일 인증)만 지원한다. B2C 사용자는 비밀번호를 새로 만들고 이메일 인증 코드를 기다리는 마찰 때문에 가입 중 이탈한다. 한국 사용자 대상 서비스에서 사실상 표준인 소셜 로그인(구글·네이버·카카오)을 붙여 "3초 가입"으로 진입 장벽을 낮추고, 이메일 인증 단계를 생략(제공자가 이미 검증)한다.

## What Changes

- 소셜 로그인 3종 추가: **구글(OIDC)**, **네이버(OAuth2)**, **카카오(OAuth2)**.
- provider 추상화 계층 도입 — 설정으로 provider를 추가할 수 있는 확장 구조(향후 Apple 등 저비용 추가).
- 소셜 로그인 성공 = "신원 확인"만 → 이후 **우리 서비스 자체 JWT(D.1) 발급**. 제공자 access token은 저장/API 사용하지 않는다.
- `users` 테이블 확장: `provider`, `provider_id` 컬럼 추가, 소셜 가입자는 `password_hash` NULL 허용.
- 소셜 가입자는 **이메일 인증(D.2) 스킵**(제공자가 이미 검증한 이메일로 간주).
- 계정 식별/연동: 같은 이메일이면 기존 로컬 계정과 통합(자동 링크). **카카오는 이메일이 선택 동의라 없을 수 있음** → 이메일 없는 경우의 식별 정책 포함.
- BFF(Vercel) 경유 OAuth 콜백(redirect) 흐름 설계 — authorize 시작·콜백 처리·state/CSRF 방어·최종 자체 토큰 전달 경로 확정.

## Capabilities

### New Capabilities
- `oauth-social-login`: 외부 OAuth/OIDC 제공자(구글·네이버·카카오)를 통한 로그인/가입, provider별 사용자 정보 정규화, 계정 자동 연동, 성공 후 자체 JWT 발급까지의 인증 흐름.

### Modified Capabilities
<!-- D.1/D.2는 OpenSpec 도입 이전이라 spec 파일이 없음. 요구사항 변경은 새 capability 델타로 흡수. -->

## Impact

- **백엔드(`auth/`)**: 신규 `auth/oauth/` 모듈(provider 추상화, 콜백 핸들러, 사용자 프로비저닝). D.1 `TokenService`(자체 JWT 발급) 재사용. `UserRepository`/`User` 엔티티에 provider 필드.
- **DB**: Flyway 신규 마이그레이션 — `users`에 `provider`, `provider_id` 추가, `password_hash` NOT NULL 제약 완화, `(provider, provider_id)` 유니크.
- **프론트(Next.js/BFF)**: 소셜 로그인 버튼 3종, authorize 리다이렉트 시작, BFF 콜백 라우트, 자체 토큰 수신·저장(기존 D.1 흐름 재사용).
- **설정/시크릿**: 제공자별 client id/secret + redirect URI(SSM Parameter Store). 구글·네이버·카카오 개발자 콘솔에 앱 등록 및 redirect URI 등록 필요.
- **의존성**: `spring-boot-starter-oauth2-client`(구글 OIDC). 네이버·카카오는 커스텀 provider 등록으로 처리.
