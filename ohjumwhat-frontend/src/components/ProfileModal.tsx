import { type ChangeEvent, type FormEvent, useRef, useState } from 'react'
import { ApiError } from '../lib/api.ts'
import {
  BIO_MAX_LENGTH,
  canRevertPhoto,
  FOOD_TAG_MAX_LENGTH,
  FOOD_TAG_RULE,
  FOOD_TAGS_MAX,
  introChange,
  NICKNAME_MAX_LENGTH,
  nicknameChange,
  type PendingPhoto,
  pendingTags,
  photoChange,
  photoFileError,
  previewPhoto,
  revertPhoto,
} from '../lib/profile.ts'
import {
  type DetailField,
  type DetailsForm,
  detailErrors,
  detailsChange,
  initialDetailsForm,
  missingDetailLabels,
} from '../lib/profileDetails.ts'
import { inputClass } from '../lib/ui.ts'
import { type Me, useChangeDetails, useChangeIntro, useChangeNickname, useChangePhoto } from '../queries/me.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import FoodTagInput, { type FoodTagState } from './FoodTagInput.tsx'
import Modal from './Modal.tsx'
import PhotoCropper from './PhotoCropper.tsx'
import ProfileDetailsFields from './ProfileDetailsFields.tsx'

type View = 'profile' | 'crop'

/**
 * 프로필 수정(Figma 03-M2): 사진·이름·한줄 소개·좋아하는 음식·상세 프로필을 한 번에 저장한다. 「사진 올리기」로 고른 사진은
 * 같은 모달의 「사진 맞추기」(03-M3)에서 맞춘다. 저장 전에는 아무것도 보내지 않으므로 취소하면 그대로다.
 * 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급)은 모두 필수라, 다 채우기 전에는 사진·이름만 바꿔도 저장할 수 없다(03-M2E).
 */
export default function ProfileModal({ me, open, onClose }: { me: Me; open: boolean; onClose: () => void }) {
  const [view, setView] = useState<View>('profile')
  const close = () => {
    setView('profile')
    onClose()
  }

  return (
    <Modal open={open} onClose={close} title={view === 'crop' ? '사진 맞추기' : '프로필 수정'}>
      <ProfileEditor me={me} view={view} onViewChange={setView} onClose={close} />
    </Modal>
  )
}

type EditorProps = { me: Me; view: View; onViewChange: (view: View) => void; onClose: () => void }

function ProfileEditor({ me, view, onViewChange, onClose }: EditorProps) {
  const [name, setName] = useState(me.name)
  const [pending, setPending] = useState<PendingPhoto>({ kind: 'none' })
  const [source, setSource] = useState<HTMLImageElement | null>(null)
  const [pickError, setPickError] = useState<string | null>(null)
  const [bio, setBio] = useState(me.bio ?? '')
  const [food, setFood] = useState<FoodTagState>({ tags: me.foodTags, draft: '', error: null })
  const [details, setDetails] = useState<DetailsForm>(() => initialDetailsForm(me.details))
  // 포커스가 한 번 벗어난 항목만 오류를 보여준다(처음 열었을 때부터 빨간 글씨로 가득하지 않게).
  // 고쳐 본 항목(dirty)은 다른 항목으로 옮겨 가도 오류를 보인다. 버튼·라디오에 포커스를 주지 않는 Safari에서
  // MBTI·취미 칩만 누르고 다른 칸으로 가면 blur가 일어나지 않기 때문이다.
  const [touched, setTouched] = useState<ReadonlySet<DetailField>>(new Set())
  const [dirty, setDirty] = useState<ReadonlySet<DetailField>>(new Set())
  const fileInput = useRef<HTMLInputElement>(null)
  const photo = useChangePhoto()
  const nickname = useChangeNickname()
  const intro = useChangeIntro()
  const detailsSave = useChangeDetails()

  const nameChange = nicknameChange(me, name)
  const change = photoChange(pending)
  // 아직 태그로 더하지 않은 글도 저장할 음식에 넣는다(적고 바로 「저장」을 눌러도 빠지지 않게). 이미 있는 음식은 건너뛴다.
  const pendingFood = pendingTags(food, FOOD_TAG_RULE)
  const introUpdate = introChange(me, bio, pendingFood.tags)
  const errors = detailErrors(details)
  const detailsIncomplete = Object.keys(errors).length > 0
  const shownErrors = Object.fromEntries(Object.entries(errors).filter(([field]) => touched.has(field as DetailField)))
  const detailUpdate = detailsChange(me, details)
  const saving = photo.isPending || nickname.isPending || intro.isPending || detailsSave.isPending
  const saveError = photo.error ?? nickname.error ?? intro.error ?? detailsSave.error

  const pick = () => fileInput.current?.click()

  const touch = (fields: Iterable<DetailField>) =>
    setTouched((prev) => {
      const next = new Set(prev)
      for (const field of fields) next.add(field)
      return next.size === prev.size ? prev : next
    })

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    // 같은 파일을 다시 골라도 change가 일어나게 비운다.
    e.target.value = ''
    if (!file) return
    const error = photoFileError(file)
    if (error) {
      setPickError(error)
      return
    }
    try {
      setSource(await loadImage(file))
      setPickError(null)
      onViewChange('crop')
    } catch {
      setPickError('이 사진 형식은 열 수 없어요. JPG나 PNG 사진을 골라 주세요.')
    }
  }

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (pendingFood.error) {
      // 음식 칸은 버튼 줄에서 멀리 있어서, 오류를 띄우고 그 칸으로 데려간다.
      setFood({ ...food, error: pendingFood.error })
      document.getElementById('food-tag')?.focus()
      return
    }
    photo.reset()
    nickname.reset()
    intro.reset()
    detailsSave.reset()
    try {
      // 사진 → 이름 → 소개 → 상세 프로필 순서로 저장한다. 뒤의 것만 실패하면 앞의 것은 저장된 채로 남고,
      // 다시 저장하면 바뀐 것만 보낸다(me가 응답으로 바뀌므로).
      if (change) {
        await photo.mutateAsync(change)
        setPending({ kind: 'none' })
      }
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
      <input ref={fileInput} type="file" accept="image/*" onChange={onFile} className="hidden" />
      {view === 'crop' && source ? (
        <PhotoCropper
          image={source}
          onBack={() => onViewChange('profile')}
          onApply={(cropped) => {
            setPending({ kind: 'upload', ...cropped })
            onViewChange('profile')
          }}
          onPickOther={pick}
        />
      ) : (
        <form onSubmit={save} className="space-y-4">
          <div className="flex flex-col items-center gap-3">
            <Avatar name={nameChange.nickname ?? me.googleName} imageUrl={previewPhoto(me, pending)} size="xl" />
            <div className="flex items-center gap-3">
              <Button variant="secondary" onClick={pick} disabled={saving}>
                사진 올리기
              </Button>
              {canRevertPhoto(me, pending) && (
                <button
                  type="button"
                  onClick={() => setPending(revertPhoto(me))}
                  disabled={saving}
                  className="text-sm font-medium text-text-secondary hover:underline disabled:opacity-50"
                >
                  구글 사진으로 되돌리기
                </button>
              )}
            </div>
            {pickError && (
              <p role="alert" className="text-sm text-text-danger">
                {pickError}
              </p>
            )}
          </div>

          <div>
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="nickname" className="text-sm font-medium">
                이름
              </label>
              {me.nickname !== null && name.trim() !== '' && (
                <button
                  type="button"
                  onClick={() => setName('')}
                  className="text-xs font-medium text-text-secondary hover:underline"
                >
                  구글 이름으로 되돌리기
                </button>
              )}
            </div>
            <input
              id="nickname"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={me.googleName}
              className={`${inputClass} mt-1.5`}
            />
            <p className={`mt-1.5 text-xs ${nameChange.length > NICKNAME_MAX_LENGTH ? 'text-text-danger' : 'text-text-tertiary'}`}>
              투표와 멤버 목록에 이 이름으로 보여요. {NICKNAME_MAX_LENGTH}자까지 쓸 수 있어요.
              {nameChange.length > NICKNAME_MAX_LENGTH && ` (지금 ${nameChange.length}자)`}
            </p>
          </div>

          <div>
            <label htmlFor="bio" className="text-sm font-medium">
              한줄 소개
            </label>
            <input
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="예: 점심은 국물 요리가 좋아요"
              className={`${inputClass} mt-1.5`}
            />
            <p className={`mt-1.5 text-xs ${introUpdate.bioLength > BIO_MAX_LENGTH ? 'text-text-danger' : 'text-text-tertiary'}`}>
              멤버가 내 프로필을 열면 보여요. {BIO_MAX_LENGTH}자까지 쓸 수 있어요.
              {introUpdate.bioLength > BIO_MAX_LENGTH && ` (지금 ${introUpdate.bioLength}자)`}
            </p>
          </div>

          <div>
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="food-tag" className="text-sm font-medium">
                좋아하는 음식
              </label>
              <span className="text-xs text-text-tertiary">
                {food.tags.length}/{FOOD_TAGS_MAX}
              </span>
            </div>
            <FoodTagInput id="food-tag" value={food} onChange={setFood} disabled={saving} />
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
          />

          {/* 모달이 길어서 내용은 스크롤되고 버튼 줄은 아래에 붙어 있다(Figma 03-M2 Actions sticky).
              저장 오류도 이 줄 안에 두어 어디까지 스크롤했든 보이게 한다. */}
          <div className="sticky bottom-0 -mx-5 space-y-2 border-t border-border-default bg-bg-surface px-5 pt-3 pb-1">
            {saveError && (
              <p role="alert" className="text-sm text-text-danger">
                {saveError instanceof ApiError ? saveError.message : '저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
              </p>
            )}
            {detailsIncomplete && (
              <p className="text-xs font-medium text-text-secondary">
                상세 프로필을 모두 채우면 저장할 수 있어요 ({missingDetailLabels(errors)} 남음)
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
                  (!change && !nameChange.changed && !introUpdate.changed && !detailUpdate.changed) ||
                  nameChange.length > NICKNAME_MAX_LENGTH ||
                  introUpdate.bioLength > BIO_MAX_LENGTH
                }
              >
                저장
              </Button>
            </div>
          </div>
        </form>
      )}
    </>
  )
}

/** 고른 파일을 그림으로 읽는다. data: 주소를 쓴다(CSP가 blob: 이미지를 막는다). 브라우저가 열 수 없는 형식이면 실패한다. */
async function loadImage(file: File): Promise<HTMLImageElement> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
  const image = new Image()
  image.src = dataUrl
  await image.decode()
  return image
}
