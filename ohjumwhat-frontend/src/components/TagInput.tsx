import { type KeyboardEvent, useRef } from 'react'
import { addTags, type TagRule, type TagState } from '../lib/profile.ts'

type Props = {
  id: string
  value: TagState
  onChange: (value: TagState) => void
  rule: TagRule
  /** 태그가 없을 때 입력 칸 안내 */
  placeholder: string
  disabled?: boolean
  /** 빨간 테두리(Figma TagInput Error) */
  invalid?: boolean
  /** 아래 안내·오류 문구의 id(aria-describedby) */
  describedBy?: string
  /** 필수 입력이면 true(aria-required) */
  required?: boolean
  /** 입력 칸 오른쪽에 「+」를 두고 이 이름을 붙인다(Figma TagInput). Enter를 모르는 사람도 더할 수 있게 한다. */
  addLabel?: string
}

/**
 * 직접 적는 태그 입력(Figma TagInput, 좋아하는 음식·취미). Enter·쉼표·입력 칸을 벗어나면 태그로 더하고,
 * ✕나 빈 칸에서 Backspace로 지운다. 개수가 차면 입력 칸을 숨긴다. 아직 더하지 않은 글(draft)도 상태로 들고 있어서
 * 「저장」을 누르면 함께 저장된다(lib/profile.ts pendingTags).
 */
export default function TagInput({ id, value, onChange, rule, placeholder, disabled, invalid, describedBy, required, addLabel }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const { tags, draft } = value

  const commit = (raw: string) => {
    const result = addTags(tags, raw, rule)
    const error = result.error ?? (result.duplicate ? rule.duplicateMessage : null)
    // 안내가 있으면(너무 김, 개수 넘음, 이미 있음) 고칠 수 있게 입력을 그대로 둔다.
    // 다만 개수가 차서 입력 칸이 사라지면 남은 글이 보이지 않으므로 비운다.
    const keep = error !== null && result.tags.length < rule.max
    onChange({ tags: result.tags, draft: keep ? raw : '', error })
  }

  const remove = (index: number) => {
    onChange({ tags: tags.filter((_, i) => i !== index), draft, error: null })
    input.current?.focus()
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중의 Enter는 글자를 끝내는 키라 태그로 더하지 않는다(조합이 끝난 뒤의 Enter만 쓴다).
    if (e.nativeEvent.isComposing || e.keyCode === 229) return
    if (e.key === 'Enter') {
      // 폼 제출(저장)을 막는다.
      e.preventDefault()
      if (draft.trim()) commit(draft)
    } else if (e.key === 'Backspace' && draft === '' && tags.length > 0) {
      e.preventDefault()
      remove(tags.length - 1)
    }
  }

  return (
    <div
      onClick={() => input.current?.focus()}
      className={`mt-1.5 flex min-h-10 flex-wrap items-center gap-1.5 rounded-lg border bg-bg-surface py-1.5 pl-1.5 focus-within:ring-2 ${
        invalid
          ? 'border-border-danger focus-within:ring-border-danger/20'
          : 'border-border-strong focus-within:border-border-brand focus-within:ring-border-brand/20'
      } ${addLabel ? 'pr-1.5' : 'pr-3'}`}
    >
      {tags.map((tag, i) => (
        <span key={tag} className="inline-flex items-center gap-1 rounded-full bg-bg-brand-soft py-1 pr-2 pl-2.5 text-xs font-medium text-text-brand">
          {tag}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation()
              remove(i)
            }}
            disabled={disabled}
            aria-label={`${tag} 지우기`}
            className="rounded-full leading-none hover:text-text-brand-strong focus-visible:outline-2 focus-visible:outline-border-brand disabled:opacity-50"
          >
            ✕
          </button>
        </span>
      ))}
      {tags.length < rule.max && (
        <>
          <input
            ref={input}
            id={id}
            value={draft}
            onChange={(e) => {
              const text = e.target.value
              // 쉼표를 치거나 「마라탕, 쌀국수」를 붙여 넣으면 바로 태그로 더한다.
              if (/[,，]/.test(text)) commit(text)
              else onChange({ tags, draft: text, error: null })
            }}
            onKeyDown={onKeyDown}
            onBlur={() => {
              if (draft.trim()) commit(draft)
            }}
            disabled={disabled}
            placeholder={tags.length === 0 ? placeholder : '더 적기'}
            enterKeyHint="done"
            aria-required={required || undefined}
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            className="min-w-24 flex-1 bg-transparent py-0.5 pl-1.5 text-sm placeholder:text-text-placeholder focus:outline-none"
          />
          {addLabel && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation()
                if (draft.trim()) commit(draft)
                input.current?.focus()
              }}
              disabled={disabled}
              aria-label={addLabel}
              className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-bg-muted text-sm font-bold text-text-secondary hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-border-brand disabled:opacity-50"
            >
              +
            </button>
          )}
        </>
      )}
    </div>
  )
}
