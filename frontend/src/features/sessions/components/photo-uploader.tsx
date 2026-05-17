"use client";

/**
 * 인증샷 업로드 컴포넌트.
 *
 * <p>설계: docs/design.md 4.2 PhotoUploader, 5.4 S3 Presigned URL 워크플로우
 *
 * <p>흐름 (파일 1개당 직렬 처리):
 * <ol>
 *   <li>POST /photos/presign → PUT presigned URL 발급</li>
 *   <li>fetch PUT 으로 S3 직접 업로드 (BFF 미경유, Authorization 헤더 없이)</li>
 *   <li>POST /sessions/{id}/photos → 메타데이터 등록</li>
 * </ol>
 *
 * <p>UX 결정:
 * <ul>
 *   <li>진행률 표시 없음 - 단순 "업로드 중..." 텍스트 (사용자 결정사항)</li>
 *   <li>여러 파일 선택 시 직렬 처리 (전체 실패가 일부만 등록되는 케이스를 단순화)</li>
 *   <li>완료/에러는 인라인 메시지 + 갤러리 invalidate</li>
 * </ul>
 *
 * <p>클라이언트 검증: 화이트리스트 contentType + 10MB 이하 (서버도 동일하게 검증).
 */
import { useQueryClient } from "@tanstack/react-query";
import { Upload } from "lucide-react";
import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  registerPhotoMeta,
  requestPresignedUpload,
  uploadToS3,
} from "@/features/sessions/photo-api";
import { extractErrorMessage } from "@/lib/api";
import { qk } from "@/lib/query-keys";

const ACCEPT_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
const MAX_BYTES = 10 * 1024 * 1024; // 10 MB

type Props = { sessionId: number };

export function PhotoUploader({ sessionId }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleClick = () => {
    if (busy) return;
    inputRef.current?.click();
  };

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setBusy(true);
    setErrorMessage(null);

    const list = Array.from(files);
    let uploaded = 0;

    for (const file of list) {
      // 클라이언트 1차 검증 (UX 빠른 피드백). 서버도 동일하게 검증.
      if (!isAllowedType(file.type)) {
        setErrorMessage(`지원하지 않는 형식입니다: ${file.name} (${file.type || "unknown"})`);
        break;
      }
      if (file.size > MAX_BYTES) {
        setErrorMessage(`파일이 10MB 를 초과합니다: ${file.name}`);
        break;
      }

      try {
        setStatusMessage(`업로드 중... (${uploaded + 1}/${list.length}) ${file.name}`);

        // 1) presigned URL
        const presign = await requestPresignedUpload({
          contentType: file.type,
          sizeBytes: file.size,
        });

        // 2) S3 직접 PUT
        await uploadToS3(presign.uploadUrl, file, file.type);

        // 3) 메타데이터 등록
        await registerPhotoMeta(sessionId, {
          s3Key: presign.s3Key,
          contentType: file.type,
          sizeBytes: file.size,
        });

        uploaded += 1;
      } catch (err) {
        setErrorMessage(extractErrorMessage(err, "업로드에 실패했습니다."));
        break;
      }
    }

    if (uploaded > 0) {
      // 사진 목록 + 세션 목록(photoCount) 갱신
      queryClient.invalidateQueries({ queryKey: qk.sessionPhotos(sessionId) });
      queryClient.invalidateQueries({ queryKey: qk.sessionsAll });
      setStatusMessage(`업로드 완료: ${uploaded}개`);
    } else {
      setStatusMessage(null);
    }

    setBusy(false);
    if (inputRef.current) inputRef.current.value = "";
  };

  return (
    <div className="rounded-lg border border-zinc-200 bg-white p-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-zinc-900">인증샷</h3>
          <p className="text-xs text-zinc-500">JPEG / PNG / WebP, 10MB 이하</p>
        </div>
        <Button onClick={handleClick} disabled={busy} aria-label="사진 업로드">
          <Upload size={14} />
          <span>{busy ? "업로드 중..." : "사진 추가"}</span>
        </Button>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT_TYPES.join(",")}
        multiple
        className="hidden"
        onChange={(e) => handleFiles(e.target.files)}
      />

      {statusMessage ? (
        <p className="mt-2 text-xs text-zinc-500" role="status" aria-live="polite">
          {statusMessage}
        </p>
      ) : null}
      {errorMessage ? (
        <p className="mt-2 text-xs text-red-600" role="alert">
          {errorMessage}
        </p>
      ) : null}
    </div>
  );
}

function isAllowedType(t: string): boolean {
  return (ACCEPT_TYPES as readonly string[]).includes(t);
}
