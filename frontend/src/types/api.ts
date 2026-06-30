/**
 * 백엔드 API 응답/요청 타입 정의.
 *
 * <p>출처: docs/design.md 3.5 엔드포인트 상세,
 *        backend/src/main/java/com/workouttracker/**\/dto/*.java
 *
 * <p>백엔드 DTO 가 변경되면 본 파일도 동기화한다. (수기 매핑이지만 MVP 에서는 OpenAPI 코드젠 대신 단순화)
 */

// ---------------------------------------------------------------------------
// 공통
// ---------------------------------------------------------------------------

/** 백엔드 표준 에러 응답. */
export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  traceId: string;
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface SignupResponse {
  userId: number;
  email: string;
  nickname: string;
  /** 가입 직후에는 false. 이메일 인증 완료 시 true. */
  emailVerified: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** 이메일 인증 코드 검증 요청. code 는 6자리 숫자 문자열(앞자리 0 보존). */
export interface VerifyEmailRequest {
  email: string;
  code: string;
}

/** 이메일 인증 코드 재발송 요청. */
export interface ResendVerificationRequest {
  email: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface MeResponse {
  userId: number;
  email: string;
  nickname: string;
}

// ---------------------------------------------------------------------------
// Exercise (운동 종류 마스터 데이터)
// ---------------------------------------------------------------------------

export type BodyPart = "CHEST" | "BACK" | "LEG" | "SHOULDER" | "ARM" | "CORE";
export type ExerciseCategory = "COMPOUND" | "ISOLATION";

export interface ExerciseResponse {
  id: number;
  code: string;
  nameKo: string;
  nameEn: string;
  bodyPart: BodyPart;
  category: ExerciseCategory;
}

export interface ExerciseListResponse {
  /** 백엔드 응답 키는 'content' (ExerciseListResponse.java) */
  content: ExerciseResponse[];
}

// ---------------------------------------------------------------------------
// Session
// ---------------------------------------------------------------------------

/** 세션 생성 요청. weightKg 는 number 로 보내고 백엔드 BigDecimal 이 받는다. */
export interface SessionCreateRequest {
  performedOn: string; // ISO yyyy-MM-dd
  memo?: string;
  exercises: SessionExerciseCreate[];
}

export interface SessionExerciseCreate {
  exerciseId: number;
  orderNo: number;
  sets: ExerciseSetCreate[];
}

export interface ExerciseSetCreate {
  setNo: number;
  weightKg: number;
  reps: number;
}

export interface SessionCreateResponse {
  sessionId: number;
}

export interface SessionListItem {
  id: number;
  performedOn: string;
  memo: string | null;
  exerciseCount: number;
  totalSets: number;
  /** BigDecimal -> JSON 직렬화 시 number. 표기 시 "1640.0 kg" 형태로 가공 (1자리 고정). */
  totalVolume: number;
  photoCount: number;
}

export interface SessionPageResponse {
  content: SessionListItem[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}

export interface SessionDetailResponse {
  id: number;
  performedOn: string;
  memo: string | null;
  createdAt: string;
  exercises: SessionExerciseDetail[];
}

export interface SessionExerciseDetail {
  orderNo: number;
  exercise: ExerciseResponse;
  sets: SessionSetDetail[];
}

export interface SessionSetDetail {
  setNo: number;
  weightKg: number;
  reps: number;
}

// ---------------------------------------------------------------------------
// Photo (Phase 5 - S3 presigned 워크플로우)
// ---------------------------------------------------------------------------

/** PUT presigned URL 발급 요청. */
export interface PresignRequest {
  contentType: string;
  sizeBytes: number;
}

export interface PresignResponse {
  uploadUrl: string;
  s3Key: string;
  expiresInSec: number;
}

/** 업로드 완료 후 메타데이터 등록 요청. */
export interface PhotoMetaRequest {
  s3Key: string;
  contentType: string;
  sizeBytes: number;
}

export interface PhotoResponse {
  id: number;
  sessionId: number;
  s3Key: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
  /** GET presigned URL (15분 만료). 갱신은 페이지 재진입 시 자동. */
  downloadUrl: string;
}

export interface PhotoListResponse {
  content: PhotoResponse[];
}

// ---------------------------------------------------------------------------
// Exercise Stats (Phase 5 - PR / 최근 기록)
// ---------------------------------------------------------------------------

export interface TopSet {
  weightKg: number;
  reps: number;
}

export interface RecentSession {
  sessionId: number;
  performedOn: string;
  topSet: TopSet | null;
}

export interface ExerciseStatsResponse {
  exerciseId: number;
  name: string;
  personalRecordKg: number | null;
  personalRecordDate: string | null;
  recentSessions: RecentSession[];
}
