export type JosaPair = '이/가' | '을/를' | '은/는' | '과/와' | '으로/로'

// [받침 있음, 받침 없음, 판단 불가]
const FORMS: Record<JosaPair, [string, string, string]> = {
  '이/가': ['이', '가', '이(가)'],
  '을/를': ['을', '를', '을(를)'],
  '은/는': ['은', '는', '은(는)'],
  '과/와': ['과', '와', '과(와)'],
  '으로/로': ['으로', '로', '(으)로'],
}

const HANGUL_FIRST = 0xac00
const HANGUL_LAST = 0xd7a3
const RIEUL = 8
// 숫자를 읽은 소리의 종성: 영 일 이 삼 사 오 육 칠 팔 구. 10·100·1000(십·백·천)도 받침이 있어 끝자리만 보면 된다.
const DIGIT_FINAL = [21, RIEUL, 0, 16, 0, 0, 1, RIEUL, RIEUL, 0]

/** 끝 글자의 종성 번호(0 = 받침 없음, 8 = ㄹ). 한글 음절·숫자가 아니면 null */
function finalOf(word: string): number | null {
  const last = word.trimEnd().at(-1)
  if (last == null) return null
  if (last >= '0' && last <= '9') return DIGIT_FINAL[Number(last)]
  const code = last.charCodeAt(0)
  if (code < HANGUL_FIRST || code > HANGUL_LAST) return null
  return (code - HANGUL_FIRST) % 28
}

/**
 * 말 뒤에 붙일 조사만 돌려준다: josa('마찬영', '이/가') → '이', josa('김민수', '이/가') → '가'.
 * 끝 글자의 받침으로 고르고(숫자는 읽는 소리), 한글·숫자로 끝나지 않으면(영어·이모지·기호) '이(가)'처럼 병기한다.
 */
export function josa(word: string, pair: JosaPair): string {
  const [withFinal, withoutFinal, unknown] = FORMS[pair]
  const final = finalOf(word)
  if (final == null) return unknown
  if (pair === '으로/로' && final === RIEUL) return withoutFinal
  return final === 0 ? withoutFinal : withFinal
}

/** 말 + 조사: withJosa('마찬영', '이/가') → '마찬영이' */
export function withJosa(word: string, pair: JosaPair): string {
  return word + josa(word, pair)
}
