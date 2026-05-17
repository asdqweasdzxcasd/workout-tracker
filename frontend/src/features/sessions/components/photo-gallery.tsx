"use client";

/**
 * 세션 사진 갤러리.
 *
 * <p>설계: docs/design.md 4.2 (세션 상세 영역), 5.4 다운로드 presigned 사용
 *
 * <p>각 사진의 {@code downloadUrl} 은 15분 만료. 만료 후에는 페이지 새로고침으로 재발급.
 * 삭제는 광학적 호출 후 invalidate (단순화).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import Image from "next/image";
import { useState } from "react";

import { deletePhoto, fetchSessionPhotos } from "@/features/sessions/photo-api";
import { extractErrorMessage } from "@/lib/api";
import { qk } from "@/lib/query-keys";

type Props = { sessionId: number };

export function PhotoGallery({ sessionId }: Props) {
  const queryClient = useQueryClient();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: qk.sessionPhotos(sessionId),
    queryFn: () => fetchSessionPhotos(sessionId),
    // downloadUrl 은 15분 만료 - 그보다 살짝 짧게 stale 처리
    staleTime: 10 * 60 * 1000,
  });

  const deleteMutation = useMutation({
    mutationFn: (photoId: number) => deletePhoto(photoId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: qk.sessionPhotos(sessionId) });
      queryClient.invalidateQueries({ queryKey: qk.sessionsAll });
    },
    onError: (err) => {
      setErrorMessage(extractErrorMessage(err, "사진 삭제에 실패했습니다."));
    },
  });

  const handleDelete = (photoId: number) => {
    if (!window.confirm("이 사진을 삭제할까요?")) return;
    setErrorMessage(null);
    deleteMutation.mutate(photoId);
  };

  if (isLoading) {
    return <p className="text-xs text-zinc-500">사진을 불러오는 중...</p>;
  }
  if (isError) {
    return (
      <p className="text-xs text-red-600" role="alert">
        {extractErrorMessage(error, "사진 목록을 불러오지 못했습니다.")}
      </p>
    );
  }
  if (!data || data.length === 0) {
    return <p className="text-xs italic text-zinc-400">등록된 사진이 없습니다.</p>;
  }

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {data.map((photo) => (
          <div
            key={photo.id}
            className="group relative aspect-square overflow-hidden rounded-md border border-zinc-200 bg-zinc-100"
          >
            {/* presigned URL 은 도메인이 매번 달라질 수 있어 next/image unoptimized 사용 */}
            <Image
              src={photo.downloadUrl}
              alt={`session-${sessionId}-photo-${photo.id}`}
              fill
              sizes="(max-width: 640px) 50vw, 33vw"
              unoptimized
              className="object-cover"
            />
            <button
              type="button"
              onClick={() => handleDelete(photo.id)}
              disabled={deleteMutation.isPending}
              className="absolute right-1 top-1 hidden rounded bg-black/60 p-1 text-white transition-opacity hover:bg-black/80 group-hover:block disabled:cursor-not-allowed"
              aria-label="사진 삭제"
            >
              <Trash2 size={14} />
            </button>
          </div>
        ))}
      </div>
      {errorMessage ? (
        <p className="text-xs text-red-600" role="alert">
          {errorMessage}
        </p>
      ) : null}
    </div>
  );
}
