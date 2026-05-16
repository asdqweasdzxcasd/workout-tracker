/**
 * 표시용 포맷터.
 *
 * <p>설계: 사용자가 확정한 표기 규약
 * <ul>
 *   <li>totalVolume: "1640.0 kg" (소수점 1자리 + kg 단위)</li>
 *   <li>날짜: "2026-05-16 (금)" 같이 한국어로 가공</li>
 * </ul>
 */
import { format } from "date-fns";
import { ko } from "date-fns/locale";

/** BigDecimal 응답을 "1640.0 kg" 형태로 변환. */
export function formatVolumeKg(value: number | string | null | undefined): string {
  const n = typeof value === "string" ? Number(value) : (value ?? 0);
  if (!Number.isFinite(n)) return "0.0 kg";
  return `${n.toFixed(1)} kg`;
}

/** 무게(kg) 단순 표기 - 세트 한 줄에 사용. */
export function formatWeightKg(value: number | string | null | undefined): string {
  const n = typeof value === "string" ? Number(value) : (value ?? 0);
  if (!Number.isFinite(n)) return "0";
  // 정수면 정수로, 소수가 있으면 1자리만 표시
  return Number.isInteger(n) ? `${n}` : n.toFixed(1);
}

/** "2026-05-16" -> "2026-05-16 (금)" */
export function formatPerformedOn(iso: string): string {
  // 시간대 영향 없이 날짜만 파싱 (YYYY-MM-DD)
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return format(date, "yyyy-MM-dd (eee)", { locale: ko });
}

/** ISO timestamp -> "2026-05-16 10:30" */
export function formatTimestamp(iso: string): string {
  return format(new Date(iso), "yyyy-MM-dd HH:mm");
}

/** 오늘 날짜를 YYYY-MM-DD 로 반환 (input[type=date] 기본값용). */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}
