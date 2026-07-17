## 1. 데이터 모델 & 마이그레이션

- [x] 1.1 Flyway 마이그레이션 작성: `users`에 `provider`, `provider_id`(nullable) 추가, `password_hash` NOT NULL → NULL 허용, `UNIQUE(provider, provider_id)`
- [x] 1.2 `User` 엔티티에 `provider`, `providerId` 필드 추가 및 로컬/소셜 불변식(로컬=hash·provider NULL / 소셜=hash NULL·provider NOT NULL) 반영
- [x] 1.3 `UserRepository`에 `findByProviderAndProviderId`, `findByEmail`(연동용) 조회 메서드 추가

## 2. Provider 설정 & 추상화

- [x] 2.1 `spring-boot-starter-oauth2-client` 의존성 추가
- [x] 2.2 구글 `ClientRegistration`(OIDC issuer 자동) 설정
- [x] 2.3 네이버·카카오 커스텀 `ClientRegistration`(authorization/token/user-info URI, scope) 설정
- [x] 2.4 client id/secret/redirect-uri를 SSM Parameter Store에서 주입하도록 설정(하드코딩 금지), ECS task-def 배선 — SSM `/workout-tracker/{GOOGLE,NAVER,KAKAO}_CLIENT_{ID,SECRET}` + `OAUTH2_{SUCCESS,FAILURE}_REDIRECT_URI` 8개 저장, task-def secrets 배선 완료(커밋 `b2f135f`)
- [x] 2.5 공통 `OAuthUserInfo`(provider, providerId, email(Optional), name) 모델 + `OAuthUserInfoExtractor` 인터페이스 정의
- [x] 2.6 구글/네이버/카카오 각 extractor 구현(카카오 `kakao_account.email` 선택 처리, 네이버 `response` 중첩 처리)

## 3. 인증 흐름 (백엔드 콜백)

- [x] 3.1 `auth/oauth/` 모듈 구조 생성(운동 도메인 무의존 유지)
- [x] 3.2 SecurityConfig에 oauth2Login 추가, authorize 진입점·콜백(`/login/oauth2/code/{provider}`) 경로 permit 설정, 미지원 provider 400 처리(필터 통과 못한 미등록 id → MVC fallback 컨트롤러)
- [x] 3.3 state(및 OIDC nonce) 생성·검증은 Spring 기본 사용, 저장소를 Redis 기반으로 배선(`RedisOAuth2AuthorizationRequestRepository`, TTL 5분, GET+DEL 원자 소비)
- [x] 3.4 로그인 성공 후 `OAuthUserInfo`로 정규화하는 파이프라인 연결(successHandler에서 registrationId→extractor 선택)

## 4. 사용자 프로비저닝 & 계정 연동

- [x] 4.1 프로비저닝 서비스: `(provider, provider_id)` 조회 → 없고 검증 이메일 있으면 같은 이메일 연동 → 둘 다 없으면 신규 생성. 전체 트랜잭션 처리
- [x] 4.2 소셜 신규 생성 시 `password_hash=NULL`, 이메일 인증됨 상태로 저장(D.2 인증 스킵)
- [x] 4.3 이메일 미제공(카카오) 시 이메일 기반 연동 스킵하고 `(provider,provider_id)`로만 식별

## 5. 자체 JWT 발급 & 프론트 전달

- [x] 5.1 successHandler → 프로비저닝 → exchange code 발급. provider access token은 NoOp authorizedClientRepository로 미저장. 자체 JWT는 교환 시점(`AuthService.issueTokensForUser`)에 발급
- [x] 5.2 1회용 exchange code(TTL 60초) Redis 저장 + 프론트 콜백으로 `?code=`만 리다이렉트(토큰 쿼리스트링 노출 금지)
- [x] 5.3 exchange code → 실제 토큰 교환 엔드포인트(`POST /api/v1/auth/oauth/exchange`, GET+DEL 1회용) 구현

## 6. 프론트엔드 (Next.js / BFF)

- [x] 6.1 로그인 화면에 구글·네이버·카카오 버튼 추가, 클릭 시 백엔드 authorize 진입점으로 이동(`NEXT_PUBLIC_OAUTH_BASE_URL`, BFF 미경유 직행)
- [x] 6.2 콜백 페이지(`/oauth/callback`): exchange code 수신 → BFF 경유 토큰 교환 → D.1 기존 저장 흐름 재사용(StrictMode 이중 소비 방지 포함)
- [x] 6.3 실패/취소(사용자 동의 거부, state 오류) 시 `/login?error=oauth` 배너 처리

## 7. 테스트

- [x] 7.1 단위: 각 extractor 정규화(구글/네이버/카카오, 카카오 이메일 없음 케이스) — 11케이스 + 이메일 미검증 드롭 케이스
- [x] 7.2 단위: 프로비저닝 3분기(신규/기존 (provider,provider_id)/이메일 연동) + 트랜잭션 — 5케이스 + 리포지토리 H2 4케이스
- [x] 7.3 통합: 콜백 state 검증(불일치→실패 리다이렉트/provider error), authorize 302(3사), 미지원 provider 400, exchange→자체 토큰 발급/무효 401/검증 400 — 8케이스(`OAuthFlowIntegrationTest`)
- [x] 7.4 E2E(가능 범위): 버튼 3종 노출·href, authorize→provider 302(state 포함), 콜백 code 누락/무효 → 실패 배너 — 6케이스(`e2e/oauth.spec.ts`). 실 provider 동의 화면은 배포 후 수동 스모크(8.2)

## 8. 마무리

- [x] 8.1 각 provider 개발자 콘솔 앱 등록 + redirect_uri(운영 도메인 + localhost) 등록 확인 — 3사 완료. 함정: 네이버 서비스 URL=프론트 도메인(disp_stat=208)·검수 전 멤버 등록, 카카오 로그인 리다이렉트 URI + 닉네임 동의항목(KOE006/KOE205)
- [x] 8.2 CI 통과 확인(단위+통합) 후 배포, 운영 스모크 테스트(3사 실제 로그인 1회씩) — ECS task-def rev:17 배포, 구글·네이버·카카오 실제 로그인 성공
- [x] 8.3 `openspec/config.yaml` 및 `docs/design.md` 부록 D.3 완료 반영, 필요 시 D.4(다중 연동) 후속 메모 — design.md D.3 완료 갱신, D.4 후속(다중 provider 연동 = user_oauth_link 테이블, 카카오 비즈앱 이메일)
