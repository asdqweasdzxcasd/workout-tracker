/**
 * 사진(인증샷) API 호출 모음.
 *
 * <p>설계: docs/design.md 3.4 / 5.4 presigned 워크플로우
 *
 * <p>업로드 흐름:
 * <ol>
 *   <li>requestPresignedUpload - BFF 경유 POST /photos/presign</li>
 *   <li>uploadToS3 - 일반 fetch 로 S3 PUT (BFF 미경유, Authorization 헤더 제거)</li>
 *   <li>registerPhotoMeta - BFF 경유 POST /sessions/{id}/photos</li>
 * </ol>
 */
import { api } from "@/lib/api";
import type {
  PhotoListResponse,
  PhotoMetaRequest,
  PhotoResponse,
  PresignRequest,
  PresignResponse,
} from "@/types/api";

/** 1단계: 백엔드에 PUT presigned URL 요청. */
export async function requestPresignedUpload(req: PresignRequest): Promise<PresignResponse> {
  const { data } = await api.post<PresignResponse>("/photos/presign", req);
  return data;
}

/**
 * 2단계: 받은 presigned URL 로 S3 에 직접 PUT.
 *
 * <p>주의:
 * <ul>
 *   <li>axios 가 아닌 fetch 사용 - Authorization 헤더가 자동 첨부되면 S3 가 SignatureDoesNotMatch 로 거부.</li>
 *   <li>Content-Type 헤더가 presign 시 명시한 값과 일치해야 한다.</li>
 *   <li>BFF 미경유 - 큰 파일이 백엔드를 통과하지 않게 함.</li>
 * </ul>
 */
export async function uploadToS3(
  uploadUrl: string,
  file: File,
  contentType: string,
): Promise<void> {
  const res = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body: file,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`S3 업로드 실패 (${res.status}): ${text || res.statusText}`);
  }
}

/** 3단계: 메타데이터 등록. */
export async function registerPhotoMeta(
  sessionId: number,
  req: PhotoMetaRequest,
): Promise<PhotoResponse> {
  const { data } = await api.post<PhotoResponse>(`/sessions/${sessionId}/photos`, req);
  return data;
}

/** 세션 사진 목록. */
export async function fetchSessionPhotos(sessionId: number): Promise<PhotoResponse[]> {
  const { data } = await api.get<PhotoListResponse>(`/sessions/${sessionId}/photos`);
  return data.content;
}

/** 사진 삭제. */
export async function deletePhoto(photoId: number): Promise<void> {
  await api.delete(`/photos/${photoId}`);
}
