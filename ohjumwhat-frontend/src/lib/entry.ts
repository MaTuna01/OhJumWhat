// 로그인 전에 보던 경로(주로 초대 링크)를 기억했다가 로그인 후 돌려보낸다.
// 구글 로그인은 같은 탭에서 다녀오므로 sessionStorage에 둔다.
const RETURN_TO_KEY = 'ohjumwhat.returnTo'

export function saveReturnTo(path: string) {
  try {
    sessionStorage.setItem(RETURN_TO_KEY, path)
  } catch {
    // 저장소를 못 쓰면 로그인 후 기본 경로로 간다.
  }
}

export function peekReturnTo(): string | null {
  try {
    return sessionStorage.getItem(RETURN_TO_KEY)
  } catch {
    return null
  }
}

export function clearReturnTo() {
  try {
    sessionStorage.removeItem(RETURN_TO_KEY)
  } catch {
    // 무시
  }
}

/** 로그인 후 이동할 경로: 기억해 둔 경로 → 최근 들어간 조직 → 마이페이지 */
export function resolveEntryPath(returnTo: string | null, lastVisitedOrgId: number | null): string {
  if (returnTo && isInternalPath(returnTo) && returnTo !== '/' && !returnTo.startsWith('/login')) {
    return returnTo
  }
  if (lastVisitedOrgId != null) {
    return `/orgs/${lastVisitedOrgId}`
  }
  return '/me'
}

function isInternalPath(path: string): boolean {
  return path.startsWith('/') && !path.startsWith('//')
}
