import { type ChangeEvent, type FormEvent, useRef, useState } from 'react'
import { ApiError } from '../lib/api.ts'
import {
  canRevertPhoto,
  NICKNAME_MAX_LENGTH,
  nicknameChange,
  type PendingPhoto,
  photoChange,
  photoFileError,
  previewPhoto,
  revertPhoto,
} from '../lib/profile.ts'
import { inputClass } from '../lib/ui.ts'
import { type Me, useChangeNickname, useChangePhoto } from '../queries/me.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'
import PhotoCropper from './PhotoCropper.tsx'

type View = 'profile' | 'crop'

/**
 * 프로필 수정(Figma 03-M2): 사진과 이름을 한 번에 저장한다. 「사진 올리기」로 고른 사진은 같은 모달의
 * 「사진 맞추기」(03-M3)에서 맞춘다. 저장 전에는 아무것도 보내지 않으므로 취소하면 그대로다.
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
  const fileInput = useRef<HTMLInputElement>(null)
  const photo = useChangePhoto()
  const nickname = useChangeNickname()

  const nameChange = nicknameChange(me, name)
  const change = photoChange(pending)
  const saving = photo.isPending || nickname.isPending
  const saveError = photo.error ?? nickname.error

  const pick = () => fileInput.current?.click()

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
    photo.reset()
    nickname.reset()
    try {
      // 사진을 먼저 저장한다. 이름 저장만 실패하면 사진은 저장된 채로 남고, 다시 저장하면 이름만 보낸다.
      if (change) {
        await photo.mutateAsync(change)
        setPending({ kind: 'none' })
      }
      if (nameChange.changed) {
        await nickname.mutateAsync(nameChange.nickname)
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

          {saveError && (
            <p role="alert" className="text-sm text-text-danger">
              {saveError instanceof ApiError ? saveError.message : '저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
            </p>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button
              type="submit"
              disabled={saving || (!change && !nameChange.changed) || nameChange.length > NICKNAME_MAX_LENGTH}
            >
              저장
            </Button>
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
