export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const found = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return found ? decodeURIComponent(found.slice(prefix.length)) : undefined
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** 화면을 떠나거나 탭을 숨기는 중에도 요청을 끝까지 보낸다(채팅 읽은 위치) */
  keepalive?: boolean
}

/**
 * 같은 도메인의 백엔드를 세션 쿠키로 호출한다. body는 JSON으로 보내고, FormData(파일 올리기)는 그대로 보낸다.
 * GET이 아닌 요청에는 서버가 내려준 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 붙인다.
 */
export async function api<T>(path: string, { method = 'GET', body, keepalive }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const form = body instanceof FormData
  // FormData는 브라우저가 경계(boundary)가 든 multipart Content-Type을 붙인다.
  if (body !== undefined && !form) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const token = readCookie('XSRF-TOKEN')
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  const res = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    keepalive,
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
  })

  if (!res.ok) {
    throw new ApiError(res.status, await errorMessage(res))
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function errorMessage(res: Response): Promise<string> {
  if (res.status === 401) {
    return '로그인이 필요해요.'
  }
  try {
    const data: unknown = await res.json()
    if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
      return data.message
    }
  } catch {
    // 본문이 JSON이 아니면 기본 문구를 쓴다.
  }
  return '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'
}
