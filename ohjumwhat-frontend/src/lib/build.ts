/**
 * 서버의 /version.json이 지금 화면 코드와 다른 빌드인지(= 배포 전부터 열려 있던 탭인지).
 * 모양이 이상한 응답(배포 중 오류 화면 등)은 새 버전으로 보지 않는다.
 */
export function isOutdated(currentBuildId: string, remote: unknown): boolean {
  if (typeof remote !== 'object' || remote === null) return false
  const { buildId } = remote as { buildId?: unknown }
  return typeof buildId === 'string' && buildId !== '' && buildId !== currentBuildId
}
