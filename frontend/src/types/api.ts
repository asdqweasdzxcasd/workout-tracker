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
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
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
