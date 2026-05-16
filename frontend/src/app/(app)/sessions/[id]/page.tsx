"use client";

/**
 * 세션 상세 페이지.
 *
 * <p>설계: docs/design.md 4.1 (app)/sessions/[id]
 *
 * <p>구성:
 * <ul>
 *   <li>useQuery 로 GET /sessions/{id}</li>
 *   <li>운동/세트 상세 표시</li>
 *   <li>삭제 버튼 → DELETE /sessions/{id} → /sessions 이동</li>
 * </ul>
 *
 * <p>Next.js 16: page params 가 Promise 이므로 React.use(params) 로 unwrap.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useState } from "react";

import { Button } from "@/components/ui/button";
import { deleteSession, fetchSession } from "@/features/sessions/api";
import { extractErrorMessage } from "@/lib/api";
import { formatPerformedOn, formatTimestamp, formatWeightKg } from "@/lib/format";
import { qk } from "@/lib/query-keys";

type Props = { params: Promise<{ id: string }> };

export default function SessionDetailPage({ params }: Props) {
  const { id } = use(params);
  const sessionId = Number(id);
  const router = useRouter();
  const queryClient = useQueryClient();
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: qk.session(sessionId),
    queryFn: () => fetchSession(sessionId),
    enabled: Number.isFinite(sessionId) && sessionId > 0,
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteSession(sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: qk.sessionsAll });
      queryClient.removeQueries({ queryKey: qk.session(sessionId) });
      router.replace("/sessions");
    },
    onError: (err) => {
      setDeleteError(extractErrorMessage(err, "세션 삭제에 실패했습니다."));
    },
  });

  const handleDelete = () => {
    if (!window.confirm("이 세션을 삭제할까요? 되돌릴 수 없습니다.")) return;
    setDeleteError(null);
    deleteMutation.mutate();
  };

  if (isLoading) {
    return <p className="py-10 text-center text-sm text-zinc-500">불러오는 중...</p>;
  }
  if (isError || !data) {
    return (
      <div className="space-y-4">
        <Link href="/sessions" className="inline-flex items-center gap-1 text-sm text-blue-600">
          <ArrowLeft size={14} />
          목록으로
        </Link>
        <div role="alert" className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
          {extractErrorMessage(error, "세션을 불러오지 못했습니다.")}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Link href="/sessions" className="inline-flex items-center gap-1 text-sm text-blue-600 hover:underline">
        <ArrowLeft size={14} />
        목록으로
      </Link>

      <div className="rounded-lg border border-zinc-200 bg-white p-5">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-lg font-bold text-zinc-900">
              {formatPerformedOn(data.performedOn)}
            </h1>
            <p className="mt-1 text-xs text-zinc-500">
              저장 시각: {formatTimestamp(data.createdAt)}
            </p>
          </div>
          <Button
            variant="danger"
            onClick={handleDelete}
            disabled={deleteMutation.isPending}
            aria-label="세션 삭제"
          >
            <Trash2 size={14} />
            <span>{deleteMutation.isPending ? "삭제 중..." : "삭제"}</span>
          </Button>
        </div>

        {data.memo ? (
          <p className="mt-3 whitespace-pre-wrap text-sm text-zinc-700">{data.memo}</p>
        ) : (
          <p className="mt-3 text-sm italic text-zinc-400">메모 없음</p>
        )}
      </div>

      {deleteError ? (
        <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {deleteError}
        </div>
      ) : null}

      <ul className="space-y-3">
        {data.exercises.map((ex) => (
          <li key={ex.orderNo} className="rounded-lg border border-zinc-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-zinc-900">
              {ex.orderNo}. {ex.exercise.nameKo}
              <span className="ml-2 text-xs font-normal text-zinc-500">
                {ex.exercise.nameEn}
              </span>
            </h2>

            <div className="mt-3 grid grid-cols-[28px_1fr_1fr] gap-2 text-xs font-medium text-zinc-500">
              <span>#</span>
              <span>무게 (kg)</span>
              <span>횟수</span>
            </div>
            <ul className="mt-1 divide-y divide-zinc-100 text-sm text-zinc-800">
              {ex.sets.map((s) => (
                <li key={s.setNo} className="grid grid-cols-[28px_1fr_1fr] gap-2 py-1.5">
                  <span className="text-zinc-500">{s.setNo}</span>
                  <span>{formatWeightKg(s.weightKg)}</span>
                  <span>{s.reps}</span>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}
