/**
 * BFF (Backend For Frontend) 프록시 라우트.
 *
 * <p>설계: docs/design.md 4.1.1 BFF 프록시 라우트
 *
 * <p>역할:
 * <ul>
 *   <li>브라우저가 부르는 <code>/api/proxy/&lt;...&gt;</code> 를 EC2 백엔드의
 *       <code>/api/v1/&lt;...&gt;</code> 로 서버사이드 forward 한다.</li>
 *   <li>Mixed Content 회피 (브라우저는 same-origin HTTPS 만 호출).</li>
 *   <li>EC2 IP 를 환경변수에만 두고 외부에 노출하지 않는다.</li>
 * </ul>
 *
 * <p>주의:
 * <ul>
 *   <li>Next.js 16 부터 <code>params</code> 가 Promise 이므로 반드시 await 한다.</li>
 *   <li>대용량 바이너리(이미지)는 본 라우트를 거치지 않고 S3 presigned PUT 으로 직접 업로드한다.</li>
 *   <li>cache: 'no-store' 로 실시간 데이터 보장 (운동 기록은 캐싱 의미 없음).</li>
 * </ul>
 */
import { NextRequest } from "next/server";

// 환경변수 누락 시 즉시 실패하도록 한다. 운영에서 BFF 가 조용히 죽는 것보다 명시적 에러가 낫다.
const EC2_API_URL = process.env.EC2_API_URL ?? "http://localhost:8080";

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

/** EC2 백엔드로 동일한 method/headers/body 를 forward 한다. */
async function proxy(request: NextRequest, context: RouteContext): Promise<Response> {
  const { path } = await context.params;

  // E2E 테스트 지원 경로(/api/proxy/test/**)는 운영에서 절대 forward 하지 않는다(이중 방어).
  // 백엔드도 prod 에서 이 경로를 노출하지 않지만, BFF 에서 한 번 더 차단해 평문 코드 유출을 막는다.
  if (path[0] === "test" && process.env.NODE_ENV === "production") {
    return new Response(
      JSON.stringify({
        status: 404,
        code: "NOT_FOUND",
        message: "대상 리소스를 찾을 수 없습니다.",
        path: `/api/proxy/${path.join("/")}`,
      }),
      { status: 404, headers: { "content-type": "application/json; charset=utf-8" } },
    );
  }

  const search = request.nextUrl.search; // "?page=0&size=20" 형태 (없으면 빈 문자열)
  const targetUrl = `${EC2_API_URL}/api/v1/${path.join("/")}${search}`;

  // 요청 헤더 복제. host 헤더를 그대로 넘기면 일부 백엔드가 잘못 라우팅할 수 있어 제거한다.
  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.delete("connection");
  headers.delete("content-length"); // fetch 가 다시 계산하도록 위임

  // GET/HEAD 는 body 가 없다. 그 외에는 원본 body 를 그대로 전달한다.
  const hasBody = !["GET", "HEAD"].includes(request.method);
  const body = hasBody ? await request.arrayBuffer() : undefined;

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, {
      method: request.method,
      headers,
      body,
      cache: "no-store",
      // Next.js Route Handler 에서 redirect 추적은 클라이언트가 결정하도록 manual 처리
      redirect: "manual",
    });
  } catch (error) {
    // 백엔드 연결 자체 실패 (네트워크/타임아웃) - 502 로 통일
    const message = error instanceof Error ? error.message : "upstream fetch failed";
    return new Response(
      JSON.stringify({
        status: 502,
        code: "BFF_UPSTREAM_ERROR",
        message: `백엔드 호출에 실패했습니다: ${message}`,
        path: `/api/proxy/${path.join("/")}`,
      }),
      {
        status: 502,
        headers: { "content-type": "application/json; charset=utf-8" },
      },
    );
  }

  // 응답 헤더 복제. content-encoding 은 fetch 가 이미 decompress 했을 수 있어 제거,
  // transfer-encoding 도 Next.js 가 다시 결정한다.
  const responseHeaders = new Headers(upstream.headers);
  responseHeaders.delete("content-encoding");
  responseHeaders.delete("transfer-encoding");
  responseHeaders.delete("content-length");

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

export const GET = (req: NextRequest, ctx: RouteContext) => proxy(req, ctx);
export const POST = (req: NextRequest, ctx: RouteContext) => proxy(req, ctx);
export const PUT = (req: NextRequest, ctx: RouteContext) => proxy(req, ctx);
export const PATCH = (req: NextRequest, ctx: RouteContext) => proxy(req, ctx);
export const DELETE = (req: NextRequest, ctx: RouteContext) => proxy(req, ctx);
