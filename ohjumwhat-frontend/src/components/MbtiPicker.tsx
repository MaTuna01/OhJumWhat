import { MBTI_AXES } from '../lib/profileDetails.ts'

type Props = {
  /** 축마다 고른 글자(아직 안 골랐으면 null) */
  value: (string | null)[]
  onChange: (value: (string | null)[]) => void
  disabled?: boolean
  invalid?: boolean
  /** 「MBTI」 제목의 id */
  labelledBy?: string
  describedBy?: string
}

/**
 * MBTI 고르기(Figma MbtiPicker·ChoicePill): 4줄(E/I, S/N, T/F, J/P) × 2개, 같은 줄에서는 하나만 고른다.
 * 줄마다 네이티브 라디오 묶음(sr-only)이라 화살표 키로도 고를 수 있다. 고른 버튼은 브랜드 배경에 흰 굵은 글씨다.
 */
export default function MbtiPicker({ value, onChange, disabled, invalid, labelledBy, describedBy }: Props) {
  return (
    <div role="group" aria-labelledby={labelledBy} aria-describedby={describedBy} className="mt-1.5 space-y-1.5">
      {MBTI_AXES.map((axis, i) => (
        <div
          key={axis[0].letter}
          role="radiogroup"
          aria-label={`${axis[0].letter} ${axis[0].label} 또는 ${axis[1].letter} ${axis[1].label}`}
          aria-required="true"
          aria-invalid={invalid || undefined}
          className="grid grid-cols-2 gap-1.5"
        >
          {axis.map((option) => {
            const selected = value[i] === option.letter
            return (
              <label
                key={option.letter}
                className={`flex h-9 cursor-pointer items-center justify-center rounded-lg text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-border-brand has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50 ${
                  selected ? 'bg-bg-brand font-bold text-text-on-brand' : 'bg-bg-muted font-medium text-text-secondary hover:bg-bg-subtle'
                }`}
              >
                <input
                  type="radio"
                  name={`mbti-${axis[0].letter}${axis[1].letter}`}
                  value={option.letter}
                  checked={selected}
                  onChange={() => onChange(value.map((letter, j) => (j === i ? option.letter : letter)))}
                  disabled={disabled}
                  className="sr-only"
                />
                {option.letter} {option.label}
              </label>
            )
          })}
        </div>
      ))}
    </div>
  )
}
