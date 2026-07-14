## ADDED Requirements

### Requirement: 소셜 로그인 시작 (Authorization 요청)

시스템은 지원하는 각 provider(구글·네이버·카카오)에 대해 OAuth/OIDC authorization 흐름을 시작하는 진입점을 SHALL 제공한다. 시작 시 CSRF 방어를 위한 `state` 값을 생성해 검증 가능한 저장소에 보관하고, provider의 authorization 엔드포인트로 사용자를 리다이렉트한다.

#### Scenario: 지원 provider로 로그인 시작
- **WHEN** 사용자가 `구글`/`네이버`/`카카오` 로그인 버튼을 눌러 해당 provider authorize 진입점을 호출하면
- **THEN** 시스템은 `state`(및 필요 시 `nonce`)를 생성·보관하고, 해당 provider의 authorization URL(client_id, redirect_uri, scope 포함)로 302 리다이렉트한다

#### Scenario: 미지원 provider 요청 거부
- **WHEN** 등록되지 않은 provider 식별자로 로그인 시작을 요청하면
- **THEN** 시스템은 400 오류를 반환하고 리다이렉트를 수행하지 않는다

### Requirement: OAuth 콜백 처리 및 state 검증

시스템은 provider 콜백을 수신하면 authorization code를 access token으로 교환하기 전에 `state` 파라미터가 시작 시 발급한 값과 일치하는지 MUST 검증한다. 불일치하거나 만료된 `state`는 인증 실패로 처리한다.

#### Scenario: 유효한 콜백
- **WHEN** provider가 유효한 `code`와 시작 시 발급한 `state`를 담아 콜백하면
- **THEN** 시스템은 code를 access token으로 교환하고 사용자 프로비저닝 단계로 진행한다

#### Scenario: state 불일치/만료
- **WHEN** 콜백의 `state`가 저장된 값과 다르거나 만료되었으면
- **THEN** 시스템은 토큰 교환을 수행하지 않고 401 인증 실패로 처리한다

#### Scenario: provider가 오류를 반환
- **WHEN** 사용자가 동의를 거부하거나 provider가 `error` 파라미터로 콜백하면
- **THEN** 시스템은 자체 토큰을 발급하지 않고 로그인 실패로 처리한다

### Requirement: Provider별 사용자 정보 정규화

시스템은 provider마다 다른 사용자 정보 응답을 공통 내부 표현(provider 식별자, provider의 고유 사용자 ID, 이메일(선택), 표시 이름(선택))으로 SHALL 정규화한다. 구글은 OIDC ID Token/UserInfo, 네이버·카카오는 각자의 UserInfo API 응답에서 필드를 추출한다.

#### Scenario: 구글 OIDC 정규화
- **WHEN** 구글 로그인이 성공해 ID Token/UserInfo를 획득하면
- **THEN** 시스템은 `provider=google`, `provider_id=sub`, `email`, `name`을 공통 표현으로 매핑한다

#### Scenario: 네이버/카카오 UserInfo 정규화
- **WHEN** 네이버 또는 카카오 로그인이 성공해 UserInfo API를 호출하면
- **THEN** 시스템은 각 provider의 응답에서 고유 ID·이메일·이름을 공통 표현으로 매핑한다(카카오는 `kakao_account.email`, 네이버는 `response.id` 등)

### Requirement: 소셜 사용자 프로비저닝

시스템은 정규화된 소셜 신원에 대해 `(provider, provider_id)`로 기존 사용자를 조회하고, 없으면 신규 사용자를 SHALL 생성한다. 소셜 가입자는 `password_hash`를 NULL로 두고, provider가 이미 검증한 이메일로 간주해 D.2 이메일 인증을 스킵한다. `(provider, provider_id)`는 유니크해야 한다.

#### Scenario: 최초 소셜 로그인 시 신규 생성
- **WHEN** 정규화된 `(provider, provider_id)`에 해당하는 사용자가 없으면
- **THEN** 시스템은 `provider`, `provider_id`, 이메일(있으면), 표시 이름을 저장한 새 사용자를 `password_hash=NULL`, 이메일 인증됨 상태로 생성한다

#### Scenario: 기존 소셜 사용자 재로그인
- **WHEN** 이미 존재하는 `(provider, provider_id)`로 로그인하면
- **THEN** 시스템은 신규 생성 없이 해당 사용자로 로그인 처리한다

### Requirement: 같은 이메일 계정 자동 연동

시스템은 소셜 신원이 검증된 이메일을 포함하고 그 이메일을 가진 기존 사용자(로컬 또는 다른 provider)가 있으면, 새 계정을 만들지 않고 기존 사용자에 해당 소셜 provider를 SHALL 연동한다. 연동은 트랜잭션으로 처리한다.

#### Scenario: 로컬 계정과 동일 이메일 연동
- **WHEN** 소셜 로그인 이메일이 기존 로컬 가입 사용자의 이메일과 일치하면
- **THEN** 시스템은 기존 사용자에 `(provider, provider_id)`를 연결하고 그 사용자로 로그인시킨다(중복 계정 생성 없음)

#### Scenario: 이메일 미제공(카카오) 시 이메일 기반 연동 안 함
- **WHEN** 소셜 신원에 이메일이 없으면(카카오 이메일 동의 미제공 등)
- **THEN** 시스템은 이메일 기반 자동 연동을 시도하지 않고 `(provider, provider_id)` 기준으로만 신규/기존을 판별한다

### Requirement: 성공 후 자체 JWT 발급

시스템은 소셜 인증·프로비저닝이 완료되면 provider의 access token을 저장하거나 API 호출에 사용하지 않고, D.1의 자체 Access/Refresh JWT를 SHALL 발급한다. 이후 모든 보호 API 접근은 자체 토큰으로만 이루어진다.

#### Scenario: 소셜 로그인 성공 → 자체 토큰 발급
- **WHEN** 소셜 인증과 사용자 프로비저닝이 성공하면
- **THEN** 시스템은 해당 사용자에 대한 자체 Access/Refresh 토큰을 D.1과 동일한 방식으로 발급하고, provider access token은 폐기한다

#### Scenario: provider 토큰 미사용
- **WHEN** 소셜 로그인이 완료된 이후 사용자가 보호 API를 호출하면
- **THEN** 접근 인가는 자체 JWT로만 검증되며 provider 토큰은 관여하지 않는다

### Requirement: BFF 경유 콜백 및 토큰 전달

시스템은 BFF(Vercel) 아키텍처에서 소셜 로그인 콜백과 최종 자체 토큰 전달 경로를 SHALL 정의한다. 자체 토큰은 URL 쿼리스트링 등 노출 경로로 전달하지 않는다.

#### Scenario: 콜백 후 프론트로 안전하게 토큰 전달
- **WHEN** 백엔드가 소셜 인증을 마치고 자체 토큰을 발급하면
- **THEN** 프론트는 D.1과 동일한 저장 흐름으로 토큰을 수신하며, 토큰은 쿼리스트링에 평문으로 노출되지 않는다

### Requirement: Provider 확장성

시스템은 새 provider 추가가 설정과 provider 어댑터 등록만으로 가능한 구조를 SHALL 유지한다. 핵심 인증 흐름(state 검증, 프로비저닝, 자체 토큰 발급)은 provider별로 중복 구현하지 않는다.

#### Scenario: 신규 provider 추가
- **WHEN** 향후 새 provider(예: Apple)를 추가하면
- **THEN** provider 설정(client id/secret/endpoint)과 정규화 어댑터만 추가하면 되고 콜백·프로비저닝·토큰 발급 로직은 수정하지 않는다
