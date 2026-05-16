"use client";

/**
 * 클라이언트 측 Provider 모음.
 *
 * <p>설계: docs/design.md 4.3 React Query 사용 패턴
 *
 * <p>현재는 React Query 만 포함. 추후 ToastProvider 등 추가 가능.
 */
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { useState } from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  // useState 로 lazy init - 컴포넌트 단위로 1회만 생성한다.
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000, // 기본 30초, 도메인별 override 가능
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={client}>
      {children}
      {process.env.NODE_ENV === "development" ? <ReactQueryDevtools initialIsOpen={false} /> : null}
    </QueryClientProvider>
  );
}
