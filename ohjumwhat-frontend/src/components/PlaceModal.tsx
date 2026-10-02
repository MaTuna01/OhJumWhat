import { useState } from 'react'
import { Link } from 'react-router'
import { type PlaceInput, type PlaceValue, EMPTY_PLACE, kakaoPlaceInput, placeInput } from '../lib/place.ts'
import { useClientConfig, useMapKey } from '../queries/config.ts'
import { useOrganization } from '../queries/orgs.ts'
import type { FoundPlace } from '../queries/places.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'
import PlaceFields from './PlaceFields.tsx'
import PlaceFinder from './PlaceFinder.tsx'

/** 고른 식당을 화면(메뉴 입력 아래 칩)에 보여줄 값. 저장하지 않는다. */
export type PickedPlace = { input: PlaceInput; name: string | null; distance: number | null }

/** 지금 붙어 있는 식당(고치기) */
export type CurrentPlace = { link: PlaceValue; kakaoPlaceId: string | null; placeQuery: string | null }

type Props = {
  orgId: number
  open: boolean
  title: string
  /** 근처 찾기·「네이버 지도에서 찾기」의 기본 검색어(메뉴 이름). 비면 근처 음식점을 둘러본다. */
  menuName: string
  current?: CurrentPlace
  confirmLabel: string
  pending?: boolean
  error?: string
  onConfirm: (picked: PickedPlace) => void
  /** 「식당 빼기」(붙어 있는 식당이 있을 때만) */
  onRemove?: () => void
  onClose: () => void
}

/**
 * 식당 찾기 모달(Figma 05-F, 05-M4): 「근처에서 찾기」(카카오 로컬, 회사 주소 기준)와 「링크 붙이기」(공유 링크).
 * 메뉴 입력의 「＋ 식당」·「근처 식당 둘러보기」, 메뉴 카드의 「식당 고치기·＋ 식당 달기」에서 연다.
 */
export default function PlaceModal({ open, title, onClose, ...props }: Props) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <PlaceModalBody {...props} onClose={onClose} />
    </Modal>
  )
}

function PlaceModalBody({ orgId, menuName, current, confirmLabel, pending, error, onConfirm, onRemove, onClose }: Omit<Props, 'open' | 'title'>) {
  const { data: config } = useClientConfig()
  const { data: org } = useOrganization(orgId)
  const keyId = useMapKey()
  const canSearch = Boolean(config?.placeSearch)
  const hasOffice = Boolean(org?.officeAddress)
  const currentIsLink = current != null && current.kakaoPlaceId == null && current.link.link !== ''
  const [tab, setTab] = useState<'search' | 'link'>(canSearch && hasOffice && !currentIsLink ? 'search' : 'link')
  const [found, setFound] = useState<{ place: FoundPlace; query: string } | null>(null)
  const [link, setLink] = useState<PlaceValue>(current?.link ?? EMPTY_PLACE)

  const selectedId = found?.place.kakaoPlaceId ?? current?.kakaoPlaceId ?? null
  const linkInput = placeInput(link)
  const initialLink = current ? placeInput(current.link) : null
  const linkUnchanged =
    initialLink != null &&
    linkInput.link === initialLink.link &&
    linkInput.placeName === initialLink.placeName &&
    linkInput.placeAddress === initialLink.placeAddress
  const canConfirm =
    tab === 'search' ? found != null && found.place.kakaoPlaceId !== current?.kakaoPlaceId : linkInput.link != null && !linkUnchanged

  const confirm = () => {
    if (tab === 'search' && found) {
      onConfirm({ input: kakaoPlaceInput(found.place.kakaoPlaceId, found.query), name: found.place.name, distance: found.place.distance })
    } else if (tab === 'link' && linkInput.link) {
      onConfirm({ input: linkInput, name: linkInput.placeName, distance: null })
    }
  }

  return (
    <div className="space-y-4">
      {canSearch && (
        <div role="tablist" aria-label="식당 붙이는 방법" className="flex gap-2">
          {(
            [
              ['search', '근처에서 찾기'],
              ['link', '링크 붙이기'],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              onClick={() => setTab(value)}
              className={`rounded-full border px-3.5 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-border-brand ${
                tab === value
                  ? 'border-border-brand bg-bg-brand-soft text-text-brand-strong'
                  : 'border-border-default bg-bg-surface text-text-secondary hover:border-border-strong'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      {tab === 'search' && org ? (
        hasOffice ? (
          <PlaceFinder org={org} keyId={keyId} initialQuery={current?.placeQuery ?? menuName} selectedId={selectedId} onSelect={(place, query) => setFound({ place, query })} />
        ) : (
          <p className="rounded-xl bg-bg-muted px-4 py-6 text-center text-sm text-text-secondary">
            조직 설정에서 회사 주소를 정하면 근처 식당을 찾을 수 있어요.
            <br />
            <Link to={`/orgs/${orgId}/settings`} onClick={onClose} className="mt-2 inline-block font-medium text-text-brand hover:underline">
              회사 주소 정하러 가기 →
            </Link>
          </p>
        )
      ) : (
        <PlaceFields value={link} onChange={setLink} searchQuery={menuName} area={org?.area ?? null} />
      )}

      {error && (
        <p role="alert" className="text-sm text-text-danger">
          {error}
        </p>
      )}
      <div className="flex items-center justify-between gap-2 pt-1">
        {onRemove ? (
          <button type="button" onClick={onRemove} disabled={pending} className="text-sm font-medium text-text-danger hover:underline disabled:opacity-50">
            식당 빼기
          </button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button onClick={confirm} disabled={pending || !canConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
