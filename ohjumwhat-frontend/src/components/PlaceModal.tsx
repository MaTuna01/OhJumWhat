import { type FormEvent, useState } from 'react'
import { type PlaceValue, placeInput } from '../lib/place.ts'
import { type PollOption, useChangePlace } from '../queries/polls.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'
import PlaceFields from './PlaceFields.tsx'

type Props = { orgId: number; pollId: number; area: string | null; option: PollOption | null; onClose: () => void }

/** 식당 달기·고치기·빼기(Figma 05-M4). 메뉴를 추가한 사람만, 투표가 진행 중일 때 연다. */
export default function PlaceModal({ orgId, pollId, area, option, onClose }: Props) {
  const changePlace = useChangePlace(orgId, pollId)
  const close = () => {
    changePlace.reset()
    onClose()
  }

  return (
    <Modal open={option != null} onClose={close} title={`${option?.name ?? ''} 식당`}>
      {option && (
        <PlaceForm
          option={option}
          area={area}
          pending={changePlace.isPending}
          error={changePlace.error?.message}
          onCancel={close}
          onSave={(input) => changePlace.mutate({ optionId: option.id, ...input }, { onSuccess: close })}
        />
      )}
    </Modal>
  )
}

type FormProps = {
  option: PollOption
  area: string | null
  pending: boolean
  error: string | undefined
  onCancel: () => void
  onSave: (input: { link: string | null; placeName: string | null }) => void
}

function PlaceForm({ option, area, pending, error, onCancel, onSave }: FormProps) {
  const initial: PlaceValue = { link: option.link ?? '', name: option.placeName ?? '' }
  const [place, setPlace] = useState(initial)
  const hasPlace = option.link != null
  const input = placeInput(place)
  const unchanged = input.link === (option.link ?? null) && input.placeName === (option.placeName ?? null)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSave(input)
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <PlaceFields value={place} onChange={setPlace} searchQuery={option.name} area={area} />
      {error && (
        <p role="alert" className="text-sm text-text-danger">
          {error}
        </p>
      )}
      <div className="flex items-center justify-between gap-2 pt-2">
        {hasPlace ? (
          <button
            type="button"
            onClick={() => onSave({ link: null, placeName: null })}
            disabled={pending}
            className="text-sm font-medium text-text-danger hover:underline disabled:opacity-50"
          >
            식당 빼기
          </button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onCancel}>
            취소
          </Button>
          <Button type="submit" disabled={pending || unchanged || (input.link == null && !hasPlace)}>
            저장
          </Button>
        </div>
      </div>
    </form>
  )
}
