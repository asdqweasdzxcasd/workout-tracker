/**
 * Next.js Proxy (구 middleware).
 *
 * <p>설계: docs/design.md 4.4 인증 가드
 *
 * <p>Next.js 16 부터 <code>middleware</code> 파일 컨벤션이 <code>proxy</code> 로 rename 되었다.
 * (deprecation notice 확인 - node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md)
 *
 * <p>Bearer + localStorage 방식에서는 서버측에서 토큰을 읽을 수 없으므로
 * 본 파일에서는 토큰 검사를 하지 않는다. 클라이언트 가드(useAuthGuard)가 보조한다.
 * 본 파일은 향후 BFF 레이어 확장(rate limit, logging 등) 을 위한 자리표시자로 유지.
 *
 * <p>현재 책임:
 * <ul>
 *   <li>API 프록시 라우트(/api/proxy/*), 정적 파일은 모두 통과</li>
 *   <li>그 외는 단순 next() - 실제 인증 가드는 (app) route group 의 layout 에서 처리</li>
 * </ul>
 */
import { NextResponse } from "next/server";

export function proxy() {
  return NextResponse.next();
}

export const config = {
  // 정적 자산/이미지/favicon 은 제외. 그 외 모든 페이지에 적용 (현재는 통과만 시킴).
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|svg|webp)$).*)"],
};
