"use client";

/**
 * 세션 목록 페이지.
 *
 * <p>설계: docs/design.md 4.1 (app)/sessions
 *
 * <p>구성:
 * <ul>
 *   <li>useQuery 로 GET /sessions?page=N&size=20 호출</li>
 *   <li>SessionCard 로 카드 표시</li>
 *   <li>"신규 작성" 버튼 → /sessions/new</li>
 *   <li>빈 상태 + 페이징 (hasNext)</li>
 * </ul>
 */
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { SessionCard } from "@/features/sessions/components/session-card";
import { fetchSessions } from "@/features/sessions/api";
import { extractErrorMessage } from "@/lib/api";
import { qk } from "@/lib/query-keys";

const PAGE_SIZE = 20;

export default function SessionsPage() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, error, isFetching } = useQuery({
    queryKey: qk.sessions(page),
    queryFn: () => fetchSessions(page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-zinc-900">내 운동 기록</h1>
        <Link href="/sessions/new">
          <Button>
            <Plus size={16} />
            <span>신규 작성</span>
          </Button>
        </Link>
      </div>

      {isLoading ? (
        <p className="py-10 text-center text-sm text-zinc-500">불러오는 중...</p>
      ) : isError ? (
        <div role="alert" className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
          {extractErrorMessage(error, "세션 목록을 불러오지 못했습니다.")}
        </div>
      ) : !data || data.content.length === 0 ? (
        <EmptyState />
      ) : (
        <>
          <ul className="space-y-3" aria-busy={isFetching}>
            {data.content.map((session) => (
              <li key={session.id}>
                <SessionCard session={session} />
              </li>
            ))}
          </ul>

          <Pager
            page={page}
            hasNext={data.hasNext}
            onPrev={() => setPage((p) => Math.max(0, p - 1))}
            onNext={() => setPage((p) => p + 1)}
          />
        </>
      )}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="rounded-lg border border-dashed border-zinc-300 bg-white p-10 text-center">
      <p className="text-sm text-zinc-500">아직 운동 기록이 없습니다.</p>
      <p className="text-sm text-zinc-500">첫 운동을 기록해보세요.</p>
      <Link href="/sessions/new" className="mt-4 inline-block">
        <Button>
          <Plus size={16} />
          <span>지금 기록하기</span>
        </Button>
      </Link>
    </div>
  );
}

type PagerProps = {
  page: number;
  hasNext: boolean;
  onPrev: () => void;
  onNext: () => void;
};

function Pager({ page, hasNext, onPrev, onNext }: PagerProps) {
  if (page === 0 && !hasNext) return null;
  return (
    <div className="flex items-center justify-between pt-2">
      <Button variant="secondary" onClick={onPrev} disabled={page === 0}>
        이전
      </Button>
      <span className="text-xs text-zinc-500">페이지 {page + 1}</span>
      <Button variant="secondary" onClick={onNext} disabled={!hasNext}>
        다음
      </Button>
    </div>
  );
}
