import { type EffectiveRestriction, type Restriction, restrictionOf } from '../lib/sanctions.ts'
import { useMe } from '../queries/me.ts'
import { useNow } from './useNow.ts'

/**
 * 관리자가 기능 type을 막았는지(내 정보의 활성 제재로 계산, 서버 SanctionGuard와 같은 규칙). 막히지 않았으면 null.
 * 끝나는 시각이 지나면 다시 불러오지 않아도 스스로 풀린다. 화면에서 막는 것은 안내용이고, 실제로는 서버가 423으로 막는다.
 */
export function useRestriction(type: Restriction): EffectiveRestriction | null {
  const { data: me } = useMe()
  const now = useNow()
  return me ? restrictionOf(me.sanctions, type, now) : null
}
