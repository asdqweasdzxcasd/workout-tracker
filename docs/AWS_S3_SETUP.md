# AWS S3 Setup Guide (Day 5 - 인증샷 업로드)

> 대상 독자: 본 프로젝트의 인프라 담당자 (이 문서를 따라가면 S3 + IAM 셋업이 완료됨)
> 전제: AWS 계정 보유, AWS CLI 또는 콘솔 접근 가능
> 리전: `ap-northeast-2` (서울)

## 0. 준비물

- AWS 콘솔 접근 권한
- 운영 EC2 인스턴스 ID (Day 6 에서 만들 예정이면 일단 S3 + IAM 정책만 만들고, IAM Role 부착은 Day 6 에 진행)

## 1. S3 버킷 생성

### 1.1 콘솔 단계

1. S3 콘솔 → "버킷 만들기"
2. 버킷 이름: `<S3-BUCKET>` (전 세계 유일해야 함, 본인 prefix 권장)
3. AWS 리전: `아시아 태평양 (서울) ap-northeast-2`
4. 객체 소유권: `ACL 비활성화됨 (권장)`
5. **퍼블릭 액세스 차단 설정: 모두 ON (기본값 유지)**
   - 모든 GET/PUT 은 presigned URL 로만 수행하므로 퍼블릭 접근 불필요
6. 버킷 버전 관리: Off (MVP, 비용 절약)
7. 기본 암호화: SSE-S3 (기본값)
8. "버킷 만들기" 클릭

### 1.2 AWS CLI 로 동일 작업

```bash
aws s3api create-bucket \
  --bucket <S3-BUCKET> \
  --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2

aws s3api put-public-access-block \
  --bucket <S3-BUCKET> \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

## 2. S3 CORS 정책

브라우저가 presigned URL 로 직접 PUT 하려면 CORS 가 필요하다.

### 2.1 CORS 설정 JSON

`bucket-cors.json`:

```json
[
  {
    "AllowedOrigins": [
      "http://localhost:3000",
      "https://<VERCEL-DOMAIN>"
    ],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

> Vercel 프로젝트의 실제 도메인이 결정되면 `AllowedOrigins` 에 추가하고 와일드카드 항목은 제거 권장.

### 2.2 적용

콘솔: 버킷 → "권한" 탭 → "CORS (교차 출처 리소스 공유)" → "편집" → 위 JSON 붙여넣기 → 저장

CLI:

```bash
aws s3api put-bucket-cors \
  --bucket <S3-BUCKET> \
  --cors-configuration file://bucket-cors.json
```

## 3. IAM 정책 (애플리케이션이 사용할 권한)

S3 버킷 내 객체에 대한 PUT / GET / DELETE 만 허용. 다른 S3 작업은 거부.

`workout-tracker-s3-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowObjectOperations",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::<S3-BUCKET>/*"
    }
  ]
}
```

> 추후 본인 폴더만 접근하도록 강화하려면 `Condition` 에 `s3:prefix` / `aws:userid` 매핑을 추가할 수 있다 (MVP 에서는 생략).

### 3.1 콘솔에서 정책 생성

1. IAM → 정책 → "정책 생성"
2. JSON 탭 선택 → 위 내용 붙여넣기
3. 정책 이름: `WorkoutTrackerS3Access`
4. 생성

## 4. 운영 EC2 인스턴스 프로파일 (Day 6 에 부착)

### 4.1 IAM Role 생성

1. IAM → 역할 → "역할 만들기"
2. 신뢰할 수 있는 엔터티 유형: `AWS 서비스`
3. 서비스: `EC2`
4. 권한 정책: 위에서 만든 `WorkoutTrackerS3Access` 선택
5. 역할 이름: `WorkoutTrackerEC2Role`
6. 생성

### 4.2 EC2 에 인스턴스 프로파일 부착

콘솔: EC2 → 인스턴스 선택 → "작업" → "보안" → "IAM 역할 수정" → 위 역할 선택 → "업데이트"

CLI:
```bash
aws ec2 associate-iam-instance-profile \
  --instance-id i-xxxxxxxxxxxxx \
  --iam-instance-profile Name=WorkoutTrackerEC2Role
```

> EC2 부착 후 백엔드는 `DefaultCredentialsProvider` 가 인스턴스 메타데이터 서비스(IMDSv2)에서 자격증명을 자동 획득한다. 코드/환경변수에 AccessKey 를 둘 필요 없음.

## 5. 로컬 개발용 IAM 사용자 (선택)

로컬 개발에서 실제 S3 를 사용하려면 IAM 사용자가 필요하다.

### 5.1 사용자 생성

1. IAM → 사용자 → "사용자 생성"
2. 사용자 이름: `workout-tracker-local-dev`
3. **콘솔 액세스 비활성화** (프로그래매틱 액세스만)
4. 권한 → "정책 직접 연결" → `WorkoutTrackerS3Access` 선택
5. 생성

### 5.2 액세스 키 발급

1. 생성된 사용자 → "보안 자격 증명" 탭 → "액세스 키 만들기"
2. 사용 사례: "AWS 외부에서 실행되는 애플리케이션"
3. 액세스 키 ID / 시크릿 액세스 키 다운로드

### 5.3 로컬 환경변수 설정

PowerShell:
```powershell
$env:AWS_ACCESS_KEY_ID = "AKIA..."
$env:AWS_SECRET_ACCESS_KEY = "..."
$env:AWS_REGION = "ap-northeast-2"
$env:AWS_S3_BUCKET = "<S3-BUCKET>"
```

bash/zsh:
```bash
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=ap-northeast-2
export AWS_S3_BUCKET=<S3-BUCKET>
```

> 또는 `~/.aws/credentials` 파일에 프로파일로 저장하고 `AWS_PROFILE` 환경변수를 사용해도 된다.

## 6. 동작 확인 체크리스트

- [ ] S3 콘솔에서 버킷 보이는지 확인 (퍼블릭 액세스 차단 표시됨)
- [ ] CORS 정책 적용됨 (편집 화면에서 JSON 확인)
- [ ] IAM 정책에 `s3:PutObject` `s3:GetObject` `s3:DeleteObject` 만 있는지 확인
- [ ] EC2 IAM Role 부착 후 인스턴스 메타데이터에서 자격증명 확인:
  ```bash
  curl -s http://169.254.169.254/latest/meta-data/iam/security-credentials/
  ```
- [ ] 백엔드 부트 로그에 S3 관련 에러 없는지 확인
- [ ] Postman 또는 curl 로 `POST /api/v1/photos/presign` 호출 → 200 + uploadUrl 응답

## 7. 비용/보안 메모

- 인증샷 평균 500KB × 사용자 10명 × 운동 30회 → 약 150MB → 무료 티어 (5GB) 내
- 라이프사이클 정책 미설정 (MVP). 추후 90일 후 Glacier 또는 삭제 정책 검토.
- presigned URL 만료: 업로드 5분 / 다운로드 15분 (코드 기본값, application.yml 로 조정 가능)
- 다른 사용자 폴더로 위변조 시도: 백엔드가 s3Key prefix(`users/{userId}/`) 검증으로 거부

## 8. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 브라우저 콘솔에 CORS 에러 | 버킷 CORS 정책의 AllowedOrigins 에 클라이언트 origin 추가 |
| presigned PUT 응답 403 SignatureDoesNotMatch | 클라이언트 Content-Type 헤더가 presign 요청 시 보낸 contentType 과 다름 |
| presigned PUT 응답 403 RequestTimeTooSkewed | 클라이언트/서버 시계 차이가 큼. NTP 동기화 |
| 백엔드 부트 시 `SdkClientException: Unable to load credentials` | IAM Role 부착 안 됨 또는 환경변수 미설정. 5.3 또는 4 진행 |
| EC2 에서 `403 Forbidden` (s3:PutObject) | IAM 정책의 Resource ARN 이 버킷명과 일치하는지 확인 |
