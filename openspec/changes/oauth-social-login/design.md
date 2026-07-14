## Context

workout-tracker는 D.1(자체 Access/Refresh JWT + Redis)과 D.2(이메일 인증)로 자체 가입 인증을 갖췄다. `auth/` 모듈은 운동 도메인과 분리돼 재사용을 목표로 한다. 이번 D.3는 외부 OAuth/OIDC 제공자(구글·네이버·카카오)로 로그인/가입을 지원한다.

핵심 제약:
- 프론트는 Next.js 16 App Router이며 **BFF는 Vercel**, 백엔드는 AWS ECS Fargate + ALB(`https://workout-api.eeiu.net`).
- 구글은 표준 **OIDC**(Spring Security `oauth2-client`가 native 지원), 네이버·카카오는 표준 OIDC가 아닌 **커스텀 OAuth2**(authorization + token + 별도 UserInfo API).
- 소셜 로그인은 "신원 확인"일 뿐 → 성공 후 **우리 자체 JWT(D.1)** 발급. provider access token은 저장/사용하지 않음.
- 시크릿(client id/secret)은 **SSM Parameter Store**. 하드코딩 금지.
- 카카오 이메일은 **선택 동의** → 없을 수 있음.

## Goals / Non-Goals

**Goals:**
- 구글·네이버·카카오 소셜 로그인/가입을 하나의 provider-추상화 흐름으로 지원.
- 소셜 인증 성공 시 D.1 `TokenService`를 재사용해 자체 JWT 발급.
- 같은 이메일 자동 연동, 소셜 가입자 이메일 인증(D.2) 스킵.
- 새 provider가 설정+어댑터 추가만으로 붙는 확장 구조.
- CSRF(state)·토큰 노출 방지 등 OAuth 보안 기본을 만족.

**Non-Goals:**
- 소셜 provider의 리소스 API 사용(친구목록·프로필사진 동기화 등) — 신원 확인만.
- 계정 **연동 해제**·다중 provider 관리 UI — 후속(D.4 이후).
- Apple 등 추가 provider 실제 구현 — 확장 가능하게만 설계, 구현은 범위 밖.
- 기존 D.1/D.2 동작 변경 — provider 필드 추가 외 로컬 가입 흐름은 유지.

## Decisions

### 1. 콜백을 어디서 처리하나 — 백엔드 직접 처리(백엔드 콜백)
- **결정**: authorize 시작과 provider 콜백을 **백엔드(Spring)에서 직접** 처리한다. redirect_uri는 백엔드 도메인(`https://workout-api.eeiu.net/oauth/callback/{provider}` 또는 Spring 기본 `/login/oauth2/code/{provider}`)으로 등록한다.
- **이유**: Spring Security `oauth2-client`가 state 생성·검증, code 교환, OIDC 검증을 이미 구현. BFF가 이 흐름을 대행하면 상용 라이브러리 이점을 버리고 보안 로직을 재구현하게 됨.
- **BFF 역할**: 프론트는 로그인 버튼 클릭 시 백엔드 authorize 진입점으로 이동만. 최종 자체 토큰은 백엔드 `successHandler`가 프론트로 안전 전달(아래 3번).
- **대안**: BFF(Vercel Route Handler)에서 OAuth 전 과정 처리 → state/nonce/PKCE를 직접 구현·유지해야 하고 백엔드와 세션 공유가 꼬임. 기각.

### 2. Provider 추상화 — Spring `ClientRegistration` + provider별 정규화 어댑터
- **결정**: 3개 provider 모두 Spring `ClientRegistrationRepository`에 등록한다. 구글은 issuer 자동설정, 네이버·카카오는 authorization/token/user-info URI를 명시한 커스텀 registration. 로그인 성공 후 provider별 UserInfo → 공통 `OAuthUserInfo`(provider, providerId, email(Optional), name)로 매핑하는 **어댑터 인터페이스**(`OAuthUserInfoExtractor`)를 provider마다 구현.
- **이유**: 콜백·state·code교환은 Spring이 공통 처리, provider 차이는 정규화 어댑터로 격리 → 새 provider = registration 1개 + extractor 1개.
- **네이버/카카오 주의**: OIDC가 아니므로 `userNameAttributeName`과 UserInfo 응답 구조가 제각각(카카오는 `id`가 최상위, 이메일은 `kakao_account.email`; 네이버는 실제 데이터가 `response` 하위에 중첩). extractor에서 흡수.

### 3. 자체 토큰을 프론트로 안전 전달 — successHandler에서 단기 1회용 코드 교환
- **결정**: 백엔드 `AuthenticationSuccessHandler`가 프로비저닝 후 D.1 JWT를 발급하되, **토큰을 쿼리스트링에 싣지 않는다**. 단기(예 60초) **1회용 exchange code**를 Redis에 저장하고 프론트 콜백 페이지로 `?code=...`만 리다이렉트 → 프론트가 BFF 경유로 그 code를 실제 Access/Refresh 토큰과 교환(POST). 교환 후 code 즉시 폐기.
- **이유**: Access/Refresh 토큰이 URL/브라우저 히스토리/리퍼러에 노출되는 것을 막음. D.1의 localStorage/Bearer 저장 흐름을 그대로 재사용 가능.
- **대안 A**: 토큰을 fragment(`#`)로 전달 → 히스토리 노출 위험·SSR 부적합. 기각.
- **대안 B**: HttpOnly 쿠키 세션 → D.1이 Bearer/localStorage 기반이라 지금 도입 시 이중화. 별도 마이그레이션(보류 항목)으로 분리.

### 4. 계정 식별·연동 순서
- **결정**: (1) `(provider, provider_id)`로 조회 → 있으면 그 사용자. (2) 없고 **검증된 이메일이 있으면** 같은 이메일 사용자 조회 → 있으면 그 계정에 provider 연동. (3) 둘 다 없으면 신규 생성(`password_hash=NULL`, 이메일 인증됨). 전 과정 **트랜잭션**.
- **이메일 없음(카카오)**: 2단계 스킵 → `(provider, provider_id)` 기준으로만 신규/기존 판별. 이메일 없는 계정은 이후 이메일 요구 기능에서 별도 처리(범위 밖, Open Question).

### 5. 데이터 모델
- **결정**: Flyway 신규 마이그레이션. `users`에 `provider VARCHAR NULL`, `provider_id VARCHAR NULL` 추가. `password_hash`를 **NOT NULL → NULL 허용**으로 완화(소셜 가입자). `UNIQUE(provider, provider_id)`. 로컬 가입자는 `provider=NULL`.
- **정합성**: 애플리케이션 레벨에서 "로컬=password_hash NOT NULL & provider NULL", "소셜=password_hash NULL & provider NOT NULL" 불변식 유지. 한 사용자가 로컬+소셜 겸용 시 provider는 다중 연동을 위해 별도 `user_oauth_link` 테이블로 분리하는 것이 정석이나, **D.3 범위에선 단일 provider 컬럼**으로 시작하고 다중 연동은 D.4에서 확장(Open Question).

## Risks / Trade-offs

- **[단일 provider 컬럼의 한계]** 한 사용자가 구글+카카오 둘 다 연동하려면 컬럼 방식으론 부족 → **Mitigation**: D.3는 "이메일 같으면 첫 provider로 연동" 수준까지만. 다중 provider는 D.4에서 `user_oauth_link` 테이블로 승격(마이그레이션 경로 확보).
- **[카카오 이메일 미제공]** 이메일 없는 소셜 계정 발생 → **Mitigation**: `(provider,provider_id)`로 항상 식별 가능. 이메일 필요한 기능(알림 등)은 사후 이메일 수집 흐름 필요(Open Question으로 남김).
- **[계정 탈취(이메일 자동 연동)]** provider가 이메일을 실제 검증했는지 신뢰 필요 → **Mitigation**: 구글/네이버/카카오 모두 이메일 인증을 자체 수행. `email_verified` 플래그가 있는 provider(구글 OIDC)는 그 값 확인, 없는 경우 provider 정책 신뢰. 자동 연동은 검증된 이메일에 한함.
- **[redirect_uri 불일치]** ECS 재생성으로 ALB DNS 변경돼도 도메인(`workout-api.eeiu.net`)은 고정 → 콘솔 등록 redirect_uri는 도메인 기준이라 영향 없음. 로컬 개발용 `localhost` redirect는 각 콘솔에 별도 등록 필요.
- **[state 저장소]** state/nonce/exchange code를 Redis(기존 ElastiCache)에 저장 → Redis 장애 시 로그인 불가. 기존 D.1도 Redis 의존이라 리스크 동일 수준.

## Migration Plan

1. Flyway 마이그레이션: `users` provider 컬럼 추가 + `password_hash` NULL 허용 + 유니크. 기존 로컬 사용자는 `provider=NULL`로 무영향.
2. 각 provider 개발자 콘솔에 앱 등록, redirect_uri(운영 도메인 + localhost) 등록, client id/secret 발급.
3. SSM Parameter Store에 provider별 시크릿 저장, ECS task-def에 배선(D.2 SES 패턴과 동일).
4. 백엔드 배포 → 프론트(소셜 버튼 + 콜백 교환) 배포.
5. **롤백**: 소셜 로그인 진입점만 제거하면 기존 로컬 로그인은 그대로 동작(컬럼은 nullable이라 스키마 롤백 불필요). 시크릿 미설정 시 provider 미등록으로 자연 비활성.

## Open Questions

- 다중 provider 연동(한 유저가 구글+카카오)을 D.3에서 어디까지? → 현재 안: 단일 컬럼으로 시작, D.4에서 링크 테이블 승격.
- 이메일 없는 카카오 계정의 사후 이메일 수집 UX가 필요한가? → D.3 범위 밖으로 두되 스키마는 이메일 NULL 허용.
- exchange-code 방식 vs 향후 HttpOnly 쿠키 전환 — 쿠키 마이그레이션(보류 항목)과 합칠지 D.5에서 재검토.
