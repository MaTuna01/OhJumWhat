// 시드 파일(ohjumwhat-backend의 loadtest 프로필이 만든 seed.json)을 읽는다.
// 형식은 com.ohjumwhat.loadtest.SeedResult와 같다: {generatedAt, closesAt, orgs[{orgId,name,pollId,optionIds[],members[{userId,googleSub,sessionCookie}]}]}
// SEED 환경변수로 경로를 바꾼다(기본 loadtest/out/seed.json). open()은 init 단계에서만 되므로 여기서 한 번 읽는다.
import { SharedArray } from 'k6/data'

const raw = JSON.parse(open(__ENV.SEED || '../out/seed.json'))

/** 모든 투표의 마감 시각(ms). 시나리오가 마감 기준 상대 시각을 계산한다. */
export const closesAtMs = Date.parse(raw.closesAt)

/** 조직 수·조직당 멤버 수(모든 조직이 같다) */
export const orgCount = raw.orgs.length
export const membersPerOrg = raw.orgs[0].members.length

/** 조직 목록(원본 구조). 조직 단위 시나리오(close-race)가 쓴다. */
export const orgs = new SharedArray('orgs', () => raw.orgs)

/** 멤버를 한 줄로 편 목록. VU마다 서로 다른 멤버를 맡긴다(한 사람은 WS 3개·채팅 10초 10개 제한이 있다). */
export const members = new SharedArray('members', () =>
  raw.orgs.flatMap((org) =>
    org.members.map((m) => ({
      userId: m.userId,
      cookie: m.sessionCookie,
      orgId: org.orgId,
      pollId: org.pollId,
      optionIds: org.optionIds,
    })),
  ),
)

/** VU 번호(1부터)에 멤버를 배정한다. VU가 멤버보다 많으면 돌아가며 쓴다(제한에 걸릴 수 있으니 시드를 늘리는 편이 낫다). */
export function memberFor(vu) {
  return members[(vu - 1) % members.length]
}

/** 조직 안의 n번째 멤버 */
export function memberOf(org, index) {
  const m = org.members[index % org.members.length]
  return { userId: m.userId, cookie: m.sessionCookie, orgId: org.orgId, pollId: org.pollId, optionIds: org.optionIds }
}
