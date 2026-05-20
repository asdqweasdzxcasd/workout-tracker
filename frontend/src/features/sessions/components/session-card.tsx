"use client";

/**
 * 세션 목록 카드.
 *
 * <p>설계: docs/design.md 4.2 SessionCard, 표시 규약: totalVolume "1640.0 kg"
 *
 * <p>photoCount 는 Phase 5 이후 표시 - 현재(0)는 숨김 처리한다.
 */
import { Calendar, Dumbbell, Image as ImageIcon, Layers } from "lucide-react";
import Link from "next/link";

import { formatPerformedOn, formatVolumeKg } from "@/lib/format";
import type { SessionListItem } from "@/types/api";

type Props = { session: SessionListItem };

export function SessionCard({ session }: Props) {
  return (
    <Link
      href={`/sessions/${session.id}`}
      className="block rounded-lg border border-zinc-200 bg-white p-4 transition-colors hover:border-blue-300 hover:bg-blue-50/40"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-medium text-zinc-900">
          <Calendar size={16} className="text-blue-600" />
          {formatPerformedOn(session.performedOn)}
        </div>
        <span className="text-xs font-semibold text-blue-700">
          {formatVolumeKg(session.totalVolume)}
        </span>
      </div>

      {session.memo ? (
        <p className="mt-2 line-clamp-2 text-sm text-zinc-600">{session.memo}</p>
      ) : (
        <p className="mt-2 text-sm italic text-zinc-400">메모 없음</p>
      )}

      <div className="mt-3 flex items-center gap-4 text-xs text-zinc-500">
        <span className="inline-flex items-center gap-1">
          <Dumbbell size={14} />
          운동 {session.exerciseCount}
        </span>
        <span className="inline-flex items-center gap-1">
          <Layers size={14} />
          {session.totalSets} 세트
        </span>
        {session.photoCount > 0 ? (
          <span className="inline-flex items-center gap-1">
            <ImageIcon size={14} />
            사진 {session.photoCount}
          </span>
        ) : null}
      </div>
    </Link>
  );
}
