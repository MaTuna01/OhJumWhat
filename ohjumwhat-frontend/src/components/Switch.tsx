type Props = {
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
  /** 스위치 옆 글의 id(aria-labelledby) */
  labelledBy?: string
  /** 설명 글의 id(aria-describedby) */
  describedBy?: string
}

/** Figma Switch. 켜짐은 brand, 꺼짐은 border/strong 트랙에 흰 손잡이, 비활성은 투명도 40% */
export default function Switch({ checked, onChange, disabled = false, labelledBy, describedBy }: Props) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand disabled:cursor-not-allowed disabled:opacity-40 ${checked ? 'bg-bg-brand' : 'bg-border-strong'}`}
    >
      <span
        aria-hidden
        className={`size-5 rounded-full bg-bg-surface shadow-sm transition-transform ${checked ? 'translate-x-[22px]' : 'translate-x-0.5'}`}
      />
    </button>
  )
}
