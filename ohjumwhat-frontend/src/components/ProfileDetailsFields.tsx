import type { FocusEvent, ReactNode } from 'react'
import { pickTag, tagKey } from '../lib/profile.ts'
import {
  ageInput,
  type DetailField,
  type DetailsForm,
  HOBBIES_MAX,
  HOBBY_MAX_LENGTH,
  HOBBY_RULE,
  HOBBY_SUGGESTIONS,
  JOB_TITLE_MAX_LENGTH,
  PERSONAL_COLORS,
} from '../lib/profileDetails.ts'
import { inputClass } from '../lib/ui.ts'
import type { PersonalColor } from '../queries/me.ts'
import MbtiPicker from './MbtiPicker.tsx'
import TagInput from './TagInput.tsx'

type Props = {
  form: DetailsForm
  /** 바뀐 항목과 함께 새 폼을 준다. */
  onChange: (form: DetailsForm, field: DetailField) => void
  /** 보여줄 오류(건드린 항목만). 보여줄 오류가 있으면 빨간 테두리와 문구 */
  errors: Partial<Record<DetailField, string>>
  /** 항목에서 포커스가 벗어났다(그 항목의 오류를 보여주기 시작한다) */
  onLeave: (field: DetailField) => void
  /** 항목에 포커스가 들어왔다. 버튼·라디오를 눌러도 포커스를 주지 않는 브라우저(Safari)에서도 앞 항목의 오류를 보이게 한다. */
  onEnter: (field: DetailField) => void
  disabled?: boolean
}

/**
 * 「프로필 수정」의 상세 프로필 섹션(Figma 03-M2 「상세 프로필」, 오류 03-M2E). 다섯 항목 모두 필수(*)다.
 * 오류는 그 항목에서 포커스가 벗어난 뒤에만 보인다(MBTI·취미는 묶음 밖으로 벗어날 때).
 */
export default function ProfileDetailsFields({ form, onChange, errors, onLeave, onEnter, disabled }: Props) {
  const set = (field: DetailField, patch: Partial<DetailsForm>) => onChange({ ...form, ...patch }, field)
  const hobbyTags = form.hobbies.tags
  const suggestions = HOBBY_SUGGESTIONS.filter((s) => !hobbyTags.some((t) => tagKey(t) === tagKey(s)))
  const fieldProps = { onLeave, onEnter }

  return (
    <div className="space-y-4 border-t border-border-default pt-4">
      <div>
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-bold">상세 프로필</h3>
          <span className="text-xs font-medium text-text-brand">모두 필수</span>
        </div>
        <p className="mt-1 text-xs text-text-tertiary">같은 조직 멤버가 내 프로필을 열면 보여요. 다섯 항목을 모두 채워야 저장할 수 있어요.</p>
      </div>

      <Field field="mbti" label="MBTI" error={errors.mbti} hint="4가지 성향에서 하나씩 골라요." group {...fieldProps}>
        <MbtiPicker
          value={form.mbti}
          onChange={(mbti) => set('mbti', { mbti })}
          disabled={disabled}
          invalid={!!errors.mbti}
          labelledBy="detail-mbti-label"
          describedBy="detail-mbti-help"
        />
      </Field>

      <Field field="personalColor" label="퍼스널컬러" htmlFor="detail-color" error={errors.personalColor} {...fieldProps}>
        <div className="relative mt-1.5">
          <select
            id="detail-color"
            value={form.personalColor}
            onChange={(e) => set('personalColor', { personalColor: e.target.value as PersonalColor | '' })}
            disabled={disabled}
            aria-required="true"
            aria-invalid={!!errors.personalColor || undefined}
            aria-describedby={errors.personalColor ? 'detail-personalColor-help' : undefined}
            className={`${inputClass} appearance-none pr-9 ${form.personalColor ? '' : 'text-text-placeholder'}`}
          >
            <option value="" disabled>
              선택하세요
            </option>
            {PERSONAL_COLORS.map((c) => (
              <option key={c.value} value={c.value} className="text-text-primary">
                {c.label}
              </option>
            ))}
          </select>
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 fill-none stroke-icon-muted stroke-2"
          >
            <path d="m6 9 6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
      </Field>

      <Field
        field="hobbies"
        label="취미"
        htmlFor="detail-hobby"
        count={`${hobbyTags.length}/${HOBBIES_MAX}`}
        error={errors.hobbies ?? form.hobbies.error ?? undefined}
        hint={
          hobbyTags.length >= HOBBIES_MAX
            ? `${HOBBIES_MAX}개를 다 적었어요. 바꾸려면 ✕로 지워 주세요.`
            : `추천을 누르거나 직접 적고 Enter·+로 추가해요. 1~${HOBBIES_MAX}개, 한 개에 ${HOBBY_MAX_LENGTH}자까지.`
        }
        group
        {...fieldProps}
      >
        <TagInput
          id="detail-hobby"
          value={form.hobbies}
          onChange={(hobbies) => set('hobbies', { hobbies })}
          rule={HOBBY_RULE}
          placeholder="취미를 적고 Enter"
          disabled={disabled}
          required
          invalid={!!errors.hobbies}
          describedBy="detail-hobbies-help"
          addLabel="취미 추가"
        />
        {hobbyTags.length < HOBBIES_MAX && suggestions.length > 0 && (
          <ul aria-label="추천 취미" className="mt-2 flex flex-wrap gap-1.5">
            {suggestions.map((s) => (
              <li key={s}>
                <button
                  type="button"
                  disabled={disabled}
                  onClick={() => set('hobbies', { hobbies: pickTag(form.hobbies, s, HOBBY_RULE) })}
                  className="rounded-full border border-border-default bg-bg-surface px-2.5 py-1 text-xs font-medium text-text-secondary hover:border-border-strong focus-visible:outline-2 focus-visible:outline-border-brand disabled:opacity-50"
                >
                  + {s}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Field>

      <Field field="age" label="나이" htmlFor="detail-age" error={errors.age} hint="숫자만 입력돼요. 1~120." {...fieldProps}>
        <div className="mt-1.5 flex items-center gap-2">
          {/* inputClass는 w-full이라 폭은 감싸는 상자로 정한다(Figma 나이 칸 96px). */}
          <div className="w-24">
            <input
              id="detail-age"
              value={form.age}
              onChange={(e) => set('age', { age: ageInput(e.target.value) })}
              inputMode="numeric"
              autoComplete="off"
              disabled={disabled}
              aria-required="true"
              aria-invalid={!!errors.age || undefined}
              aria-describedby="detail-age-help"
              placeholder="예: 32"
              className={inputClass}
            />
          </div>
          <span className="text-sm text-text-secondary">세</span>
        </div>
      </Field>

      <Field
        field="jobTitle"
        label="직급"
        htmlFor="detail-job"
        error={errors.jobTitle}
        hint={`${JOB_TITLE_MAX_LENGTH}자까지 쓸 수 있어요.`}
        {...fieldProps}
      >
        <input
          id="detail-job"
          value={form.jobTitle}
          onChange={(e) => set('jobTitle', { jobTitle: e.target.value })}
          disabled={disabled}
          aria-required="true"
          aria-invalid={!!errors.jobTitle || undefined}
          aria-describedby="detail-jobTitle-help"
          placeholder="예: 개발팀 매니저"
          className={`${inputClass} mt-1.5`}
        />
      </Field>
    </div>
  )
}

type FieldProps = {
  field: DetailField
  label: string
  /** 입력 하나짜리 항목의 id(label for). 묶음(MBTI)은 제목 id(detail-…-label)로 이어 준다. */
  htmlFor?: string
  count?: string
  error?: string
  hint?: string
  onLeave: (field: DetailField) => void
  onEnter: (field: DetailField) => void
  /** 묶음(여러 버튼·입력)이면 묶음 밖으로 포커스가 나갈 때만 벗어난 것으로 본다. */
  group?: boolean
  children: ReactNode
}

function Field({ field, label, htmlFor, count, error, hint, onLeave, onEnter, group, children }: FieldProps) {
  const outside = (e: FocusEvent<HTMLDivElement>) => !e.currentTarget.contains(e.relatedTarget as Node | null)
  const Label = htmlFor ? 'label' : 'p'
  return (
    <div
      onFocus={(e) => {
        if (!group || outside(e)) onEnter(field)
      }}
      onBlur={(e) => {
        if (!group || outside(e)) onLeave(field)
      }}
    >
      <div className="flex items-center justify-between gap-2">
        <Label {...(htmlFor ? { htmlFor } : { id: `detail-${field}-label` })} className="text-sm font-medium">
          {label} <span aria-hidden="true" className="text-text-brand">*</span>
        </Label>
        {count && <span className="text-xs text-text-tertiary">{count}</span>}
      </div>
      {children}
      {/* 안내와 오류는 다른 요소로 그린다(이미 있는 문단에 role="alert"를 붙이면 읽지 않는 스크린 리더가 많다). */}
      {error ? (
        <p key="error" id={`detail-${field}-help`} role="alert" className="mt-1.5 text-xs text-text-danger">
          {error}
        </p>
      ) : (
        hint && (
          <p key="hint" id={`detail-${field}-help`} className="mt-1.5 text-xs text-text-tertiary">
            {hint}
          </p>
        )
      )}
    </div>
  )
}
