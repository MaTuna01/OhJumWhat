import { type FormEvent, useRef, useState } from 'react'
import { ApiError } from '../lib/api.ts'
import {
  BIO_MAX_LENGTH,
  FOOD_TAG_MAX_LENGTH,
  FOOD_TAG_RULE,
  FOOD_TAGS_MAX,
  introChange,
  NICKNAME_MAX_LENGTH,
  nicknameChange,
  pendingTags,
} from '../lib/profile.ts'
import {
  type DetailField,
  type DetailsForm,
  detailErrors,
  detailsChange,
  initialDetailsForm,
  isBlankDetailsForm,
  missingDetailLabels,
} from '../lib/profileDetails.ts'
import { inputClass } from '../lib/ui.ts'
import {
  type AdminUserDetail,
  useAdminChangeDetails,
  useAdminChangeIntro,
  useAdminChangeNickname,
  useAdminClearDetails,
} from '../queries/admin.ts'
import Button from './Button.tsx'
import ConfirmDialog from './ConfirmDialog.tsx'
import FoodTagInput, { type FoodTagState } from './FoodTagInput.tsx'
import Modal from './Modal.tsx'
import ProfileDetailsFields from './ProfileDetailsFields.tsx'

type Props = { detail: AdminUserDetail; open: boolean; onClose: () => void }

/**
 * 관리자 회원 프로필 수정(Figma A03-M3): 이름(별명)·한줄 소개·좋아하는 음식·상세 프로필을 「저장」 한 번에 고친다.
 * 규칙·문구는 마이페이지 「프로필 수정」(03-M2, ProfileModal)과 같고, 본인에게 따로 알리지 않는다. 사진은 회원 상세의 「지우기」로만 지운다.
 * 상세 프로필이 비어 있던 회원은 비워 둔 채 저장할 수 있다(채우기 시작했으면 다섯 항목 모두). 채운 회원은 고치거나
 * 「상세 프로필 지우기」로 다섯 항목을 한꺼번에 지운다.
 */
export default function AdminProfileModal({ detail, open, onClose }: Props) {
  return (
    <Modal open={open} onClose={onClose} title="프로필 수정">
      <AdminProfileEditor detail={detail} onClose={onClose} />
    </Modal>
  )
}

function AdminProfileEditor({ detail, onClose }: Omit<Props, 'open'>) {
  const { user } = detail
  const [name, setName] = useState(user.name)
  const [bio, setBio] = useState(detail.bio ?? '')
  const [food, setFood] = useState<FoodTagState>({ tags: detail.foodTags, draft: '', error: null })
  const [details, setDetails] = useState<DetailsForm>(() => initialDetailsForm(detail.details))
  // 오류를 보여주는 규칙은 ProfileModal과 같다(포커스가 벗어난 항목, 고쳐 본 항목은 다른 항목으로 옮겨 갈 때).
  const [touched, setTouched] = useState<ReadonlySet<DetailField>>(new Set())
  const [dirty, setDirty] = useState<ReadonlySet<DetailField>>(new Set())
  const [clearing, setClearing] = useState(false)
  const nickname = useAdminChangeNickname(user.id)
  const intro = useAdminChangeIntro(user.id)
  const detailsSave = useAdminChangeDetails(user.id)
  const clearDetails = useAdminClearDetails(user.id)

  const nameChange = nicknameChange({ name: user.name, nickname: detail.nickname }, name)
  // 아직 태그로 더하지 않은 글도 저장할 음식에 넣는다(ProfileModal과 같다).
  const pendingFood = pendingTags(food, FOOD_TAG_RULE)
  const introUpdate = introChange(detail, bio, pendingFood.tags)
  const detailsHeading = useRef<HTMLHeadingElement>(null)
  const hasDetails = detail.details !== null
  // 비어 있던 회원의 상세 프로필을 비워 둔 채면 건너뛴다. 하나라도 채웠으면 마이페이지처럼 다섯 항목이 모두 있어야 저장된다.
  const skipDetails = !hasDetails && isBlankDetailsForm(details)
  const errors = skipDetails ? {} : detailErrors(details)
  const detailsIncomplete = Object.keys(errors).length > 0
  const shownErrors = Object.fromEntries(Object.entries(errors).filter(([field]) => touched.has(field as DetailField)))
  const detailUpdate = skipDetails ? { details: null, changed: false } : detailsChange(detail, details)
  const saving = nickname.isPending || intro.isPending || detailsSave.isPending || clearDetails.isPending
  const saveError = nickname.error ?? intro.error ?? detailsSave.error

  const touch = (fields: Iterable<DetailField>) =>
    setTouched((prev) => {
      const next = new Set(prev)
      for (const field of fields) next.add(field)
      return next.size === prev.size ? prev : next
    })

  /** 상세 프로필 입력을 처음(빈 폼)으로 */
  const resetDetails = () => {
    setDetails(initialDetailsForm(null))
    setTouched(new Set())
    setDirty(new Set())
  }

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (pendingFood.error) {
      setFood({ ...food, error: pendingFood.error })
      document.getElementById('admin-food-tag')?.focus()
      return
    }
    nickname.reset()
    intro.reset()
    detailsSave.reset()
    try {
      // 이름 → 소개 → 상세 프로필 순서로 바뀐 것만 저장한다. 뒤의 것만 실패하면 앞의 것은 저장된 채로 남고,
      // 다시 저장하면 남은 것만 보낸다(성공하면 회원 상세를 다시 불러오므로).
      if (nameChange.changed) {
        await nickname.mutateAsync(nameChange.nickname)
      }
      if (introUpdate.changed) {
        await intro.mutateAsync({ bio: introUpdate.bio, foodTags: introUpdate.foodTags })
        setFood({ tags: introUpdate.foodTags, draft: '', error: null })
      }
      if (detailUpdate.changed && detailUpdate.details) {
        const saved = await detailsSave.mutateAsync(detailUpdate.details)
        setDetails(initialDetailsForm(saved.details))
      }
      onClose()
    } catch {
      // 오류는 뮤테이션 상태(saveError)로 보여준다.
    }
  }

  return (
    <>
      <form onSubmit={save} className="space-y-4">
        <p className="text-sm text-text-tertiary">{user.name}님의 프로필을 고쳐요. 본인에게 따로 알리지 않아요.</p>

        <div>
          <label htmlFor="admin-nickname" className="text-sm font-medium">
            이름(별명)
          </label>
          <input
            id="admin-nickname"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={user.googleName}
            className={`${inputClass} mt-1.5`}
          />
          <p className={`mt-1.5 text-xs ${nameChange.length > NICKNAME_MAX_LENGTH ? 'text-text-danger' : 'text-text-tertiary'}`}>
            비우면 구글 이름({user.googleName})으로 보여요. {NICKNAME_MAX_LENGTH}자까지 쓸 수 있어요.
            {nameChange.length > NICKNAME_MAX_LENGTH && ` (지금 ${nameChange.length}자)`}
          </p>
        </div>

        <div>
          <label htmlFor="admin-bio" className="text-sm font-medium">
            한줄 소개
          </label>
          <input
            id="admin-bio"
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="비우면 소개를 지워요"
            className={`${inputClass} mt-1.5`}
          />
          <p className={`mt-1.5 text-xs ${introUpdate.bioLength > BIO_MAX_LENGTH ? 'text-text-danger' : 'text-text-tertiary'}`}>
            {BIO_MAX_LENGTH}자까지 쓸 수 있어요.
            {introUpdate.bioLength > BIO_MAX_LENGTH && ` (지금 ${introUpdate.bioLength}자)`}
          </p>
        </div>

        <div>
          <div className="flex items-center justify-between gap-2">
            <label htmlFor="admin-food-tag" className="text-sm font-medium">
              좋아하는 음식
            </label>
            <span className="text-xs text-text-tertiary">
              {food.tags.length}/{FOOD_TAGS_MAX}
            </span>
          </div>
          <FoodTagInput id="admin-food-tag" value={food} onChange={setFood} disabled={saving} />
          <p role={food.error ? 'alert' : undefined} className={`mt-1.5 text-xs ${food.error ? 'text-text-danger' : 'text-text-tertiary'}`}>
            {food.error ??
              (food.tags.length >= FOOD_TAGS_MAX
                ? `${FOOD_TAGS_MAX}개를 다 적었어요. 바꾸려면 ✕로 지워 주세요.`
                : `Enter나 쉼표로 추가해요. 한 개에 ${FOOD_TAG_MAX_LENGTH}자까지 적을 수 있어요.`)}
          </p>
        </div>

        <ProfileDetailsFields
          form={details}
          onChange={(next, field) => {
            setDetails(next)
            setDirty((prev) => (prev.has(field) ? prev : new Set(prev).add(field)))
          }}
          errors={shownErrors}
          onLeave={(field) => touch([field])}
          onEnter={(field) => touch([...dirty].filter((f) => f !== field))}
          disabled={saving}
          description={
            hasDetails
              ? '다섯 항목을 함께 고치거나, 「상세 프로필 지우기」로 한꺼번에 지워요. 하나만 지울 수는 없어요.'
              : '비워 두면 그대로 두어요. 채우려면 다섯 항목을 모두 채워야 저장할 수 있어요.'
          }
          headingRef={detailsHeading}
          headerAction={
            hasDetails ? (
              <button
                type="button"
                onClick={() => setClearing(true)}
                disabled={saving}
                className="text-xs font-medium text-text-danger hover:underline disabled:opacity-50"
              >
                상세 프로필 지우기
              </button>
            ) : !skipDetails ? (
              <button
                type="button"
                onClick={resetDetails}
                disabled={saving}
                className="text-xs font-medium text-text-secondary hover:underline disabled:opacity-50"
              >
                입력 비우기
              </button>
            ) : null
          }
        />

        {/* 버튼 줄은 ProfileModal처럼 아래에 붙어 있고, 저장 오류도 이 줄 안에 둔다. */}
        <div className="sticky bottom-0 -mx-5 space-y-2 border-t border-border-default bg-bg-surface px-5 pt-3 pb-1">
          {saveError && (
            <p role="alert" className="text-sm text-text-danger">
              {saveError instanceof ApiError ? saveError.message : '저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
            </p>
          )}
          {detailsIncomplete && (
            <p className="text-xs font-medium text-text-secondary">
              {`${hasDetails ? '상세 프로필을 모두 채우면' : '상세 프로필을 모두 채우거나 비우면'} 저장할 수 있어요 (${missingDetailLabels(errors)} 남음)`}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button
              type="submit"
              disabled={
                saving ||
                detailsIncomplete ||
                (!nameChange.changed && !introUpdate.changed && !detailUpdate.changed) ||
                nameChange.length > NICKNAME_MAX_LENGTH ||
                introUpdate.bioLength > BIO_MAX_LENGTH
              }
            >
              저장
            </Button>
          </div>
        </div>
      </form>

      {/* <dialog> 안의 <dialog>라 이 창을 닫아도 프로필 수정은 그대로다(Modal이 자기 close만 받는다). */}
      <ConfirmDialog
        open={clearing}
        onClose={() => {
          clearDetails.reset()
          setClearing(false)
        }}
        onConfirm={() =>
          clearDetails.mutate(undefined, {
            onSuccess: () => {
              resetDetails()
              setClearing(false)
              // 지우기 버튼이 사라지므로 포커스를 상세 프로필 제목으로 옮긴다.
              requestAnimationFrame(() => detailsHeading.current?.focus())
            },
          })
        }
        title="상세 프로필을 모두 지울까요?"
        confirmLabel="지우기"
        danger
        pending={clearDetails.isPending}
        error={
          clearDetails.error
            ? clearDetails.error instanceof ApiError
              ? clearDetails.error.message
              : '지우지 못했어요. 연결을 확인하고 다시 시도해 주세요.'
            : undefined
        }
      >
        <p>
          MBTI·퍼스널컬러·취미·나이·직급을 모두 지워요. 「저장」을 누르지 않아도 바로 지워지고, 본인에게 따로 알리지 않아요. 다음에 들어오면 다시
          채우기 안내가 보여요.
        </p>
      </ConfirmDialog>
    </>
  )
}
