"use client";

/**
 * 운동별 PR / 통계 페이지.
 *
 * <p>설계: docs/design.md 4.1 (app)/exercises/[id], 3.5 GET /exercises/{id}/stats
 *
 * <p>구성:
 * <ul>
 *   <li>PR 카드 (최고 무게 + 달성일)</li>
 *   <li>최근 5 세션 + 각 세션의 topSet (weight x reps)</li>
 *   <li>세션 항목 클릭 시 /sessions/[id] 로 이동</li>
 * </ul>
 */
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Trophy } from "lucide-react";
import Link from "next/link";
import { use } from "react";

import { fetchExerciseStats } from "@/features/exercises/api";
import { extractErrorMessage } from "@/lib/api";
import { formatPerformedOn, formatWeightKg } from "@/lib/format";
import { qk } from "@/lib/query-keys";

type Props = { params: Promise<{ id: string }> };

export default function ExerciseStatsPage({ params }: Props) {
  const { id } = use(params);
  const exerciseId = Number(id);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: qk.exerciseStats(exerciseId),
    queryFn: () => fetchExerciseStats(exerciseId),
    enabled: Number.isFinite(exerciseId) && exerciseId > 0,
  });

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
          {extractErrorMessage(error, "운동 통계를 불러오지 못했습니다.")}
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

      <header>
        <h1 className="text-xl font-bold text-zinc-900">{data.name}</h1>
        <p className="text-xs text-zinc-500">운동 ID: {data.exerciseId}</p>
      </header>

      <section className="rounded-lg border border-amber-200 bg-amber-50 p-5">
        <div className="flex items-center gap-3">
          <Trophy size={28} className="text-amber-500" />
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-amber-700">
              Personal Record
            </p>
            {data.personalRecordKg != null ? (
              <>
                <p className="text-2xl font-bold text-zinc-900">
                  {formatWeightKg(data.personalRecordKg)} kg
                </p>
                {data.personalRecordDate ? (
                  <p className="text-xs text-zinc-500">
                    달성일: {formatPerformedOn(data.personalRecordDate)}
                  </p>
                ) : null}
              </>
            ) : (
              <p className="text-sm italic text-zinc-500">아직 기록이 없습니다.</p>
            )}
          </div>
        </div>
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-zinc-900">최근 세션</h2>
        {data.recentSessions.length === 0 ? (
          <p className="text-sm italic text-zinc-400">최근 기록이 없습니다.</p>
        ) : (
          <ul className="space-y-2">
            {data.recentSessions.map((rs) => (
              <li key={rs.sessionId}>
                <Link
                  href={`/sessions/${rs.sessionId}`}
                  className="block rounded-lg border border-zinc-200 bg-white p-3 transition-colors hover:border-blue-300 hover:bg-blue-50/40"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-zinc-700">
                      {formatPerformedOn(rs.performedOn)}
                    </span>
                    {rs.topSet ? (
                      <span className="text-xs font-semibold text-blue-700">
                        {formatWeightKg(rs.topSet.weightKg)} kg × {rs.topSet.reps}
                      </span>
                    ) : (
                      <span className="text-xs italic text-zinc-400">기록 없음</span>
                    )}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
