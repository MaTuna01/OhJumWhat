import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import Button from '../components/Button.tsx'
import InviteLinkField from '../components/InviteLinkField.tsx'
import LeaveOrgDialog from '../components/LeaveOrgDialog.tsx'
import MemberList from '../components/MemberList.tsx'
import NaverMap from '../components/NaverMap.tsx'
import { Section } from '../components/PageState.tsx'
import PlaceFields from '../components/PlaceFields.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { formatDistance } from '../lib/distance.ts'
import { serviceLabel } from '../lib/link.ts'
import { type PlaceValue, placeInput } from '../lib/place.ts'
import { columnsClass, inputClass } from '../lib/ui.ts'
import { useMapKey } from '../queries/config.ts'
import { SEARCH_RADII, type Organization, useOrganization, useRenameOrganization, useUpdateOrgLocation } from '../queries/orgs.ts'
import { usePlaces } from '../queries/places.ts'

export default function OrgSettingsPage() {
  const orgId = useOrgId()
  const { data: org } = useOrganization(orgId)
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)
  const [renamed, setRenamed] = useState(false)
  const [located, setLocated] = useState(false)
  useDocumentTitle('설정', org?.name)

  // "저장했어요"는 잠깐만 보여준다. (저장 후 입력창이 새로 그려지므로 상태는 여기서 들고 있다)
  useEffect(() => {
    if (!renamed && !located) return
    const timer = setTimeout(() => {
      setRenamed(false)
      setLocated(false)
    }, 2500)
    return () => clearTimeout(timer)
  }, [renamed, located])

  if (!org) return null

  // 모바일은 DOM 순서(초대 링크 → 이름 → 위치 → 멤버 → 탈퇴)대로 쌓고,
  // 데스크톱은 멤버만 오른쪽 사이드로 보낸다. 마지막 행(1fr)이 남는 높이를 받아 왼쪽 카드 사이가 벌어지지 않는다.
  return (
    <div className={`flex flex-col gap-6 lg:grid-rows-[auto_auto_auto_1fr] ${columnsClass}`}>
      <Section title="초대 링크" className="lg:col-start-1">
        <p className="mb-3 text-sm text-text-tertiary">이 링크를 받은 사람은 누구나 조직에 참여할 수 있어요.</p>
        <InviteLinkField token={org.inviteToken} />
      </Section>

      <Section
        title="조직 이름"
        className="lg:col-start-1"
        action={
          renamed && (
            <span role="status" className="text-sm font-medium text-text-success">
              ✓ 저장했어요
            </span>
          )
        }
      >
        {/* 이름이 바뀌면(다른 멤버가 바꾼 경우 포함) 입력창을 새 값으로 초기화한다. */}
        <RenameForm key={org.name} org={org} onRenamed={() => setRenamed(true)} />
      </Section>

      <Section
        title="조직 위치"
        className="lg:col-start-1"
        action={
          located && (
            <span role="status" className="text-sm font-medium text-text-success">
              ✓ 저장했어요
            </span>
          )
        }
      >
        <LocationForm
          key={`${org.area}|${org.officeLink}|${org.officeName}|${org.officeAddress}|${org.searchRadius}`}
          org={org}
          onSaved={() => setLocated(true)}
        />
      </Section>

      <MemberList orgId={orgId} className="lg:col-start-2 lg:row-span-4 lg:row-start-1" />

      <Section title="조직 탈퇴" className="lg:col-start-1">
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm text-text-tertiary">
            {org.memberCount <= 1 ? '마지막 멤버라서 탈퇴하면 조직이 삭제돼요.' : '탈퇴해도 조직과 다른 멤버의 기록은 남아요.'}
          </p>
          <Button variant="secondary" className="shrink-0 text-text-danger" onClick={() => setLeaving(true)}>
            탈퇴
          </Button>
        </div>
      </Section>

      <LeaveOrgDialog org={leaving ? org : null} onClose={() => setLeaving(false)} onLeft={() => navigate('/me', { replace: true })} />
    </div>
  )
}

function RenameForm({ org, onRenamed }: { org: Organization; onRenamed: () => void }) {
  const [name, setName] = useState(org.name)
  const rename = useRenameOrganization(org.id)
  const trimmed = name.trim()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    rename.mutate(trimmed, { onSuccess: onRenamed })
  }

  return (
    <form onSubmit={submit}>
      <div className="flex gap-2">
        <input
          aria-label="조직 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={50}
          className={inputClass}
        />
        <Button type="submit" className="shrink-0" disabled={!trimmed || trimmed === org.name || rename.isPending}>
          저장
        </Button>
      </div>
      {rename.error && (
        <p role="alert" className="mt-2 text-sm text-text-danger">
          {rename.error.message}
        </p>
      )}
    </form>
  )
}

/**
 * Figma 07-L·D07-L 조직 위치: 회사 위치(지도 링크·이름)와 회사 주소, 검색 반경, 검색 지역. 통째로 저장한다.
 * 회사 주소는 근처 식당 검색·지도의 기준점이라 서버가 찾을 수 있는 주소인지 확인한다. 공유 글을 붙이면 이름·주소를 채운다.
 */
function LocationForm({ org, onSaved }: { org: Organization; onSaved: () => void }) {
  const [area, setArea] = useState(org.area ?? '')
  const [office, setOffice] = useState<PlaceValue>({
    link: org.officeLink ?? '',
    name: org.officeName ?? '',
    address: org.officeAddress ?? '',
  })
  const [radius, setRadius] = useState(org.searchRadius)
  const update = useUpdateOrgLocation(org.id)
  const mapKey = useMapKey()
  const { link: officeLink, placeName: officeName } = placeInput(office)
  // 회사 주소는 지도 링크가 없어도 저장한다.
  const officeAddress = office.address.trim() || null
  const nextArea = area.trim() || null
  const unchanged =
    nextArea === org.area &&
    officeLink === org.officeLink &&
    officeName === org.officeName &&
    officeAddress === org.officeAddress &&
    radius === org.searchRadius

  const submit = (e: FormEvent) => {
    e.preventDefault()
    update.mutate({ area: nextArea, officeLink, officeName, officeAddress, searchRadius: radius }, { onSuccess: onSaved })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-sm text-text-tertiary">근처 식당 검색과 지도는 회사 주소를 기준으로 해요.</p>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <p className="text-sm font-medium">회사 위치</p>
          {org.officeLink && (
            <a
              href={org.officeLink}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex max-w-full items-center rounded-full border border-border-default bg-bg-surface px-2 py-0.5 text-xs font-medium text-text-secondary hover:border-border-strong hover:text-text-primary focus-visible:outline-2 focus-visible:outline-border-brand"
            >
              <span className="truncate">
                {org.officeName ?? '회사'} · {serviceLabel(org.officeLink)} ↗
              </span>
            </a>
          )}
          <PlaceFields
            value={office}
            onChange={setOffice}
            searchQuery={office.name || org.name}
            area={nextArea}
            nameLabel="회사 이름"
            linkLabel="회사 지도 링크"
            showAddress={false}
          />
          <div className="pt-2.5">
            <label htmlFor="org-office-address" className="text-sm font-medium">
              회사 주소
            </label>
            <input
              id="org-office-address"
              value={office.address}
              onChange={(e) => setOffice({ ...office, address: e.target.value })}
              maxLength={200}
              placeholder="예: 서울 강남구 테헤란로 152"
              className={`${inputClass} mt-1.5`}
            />
            <p className="mt-1.5 text-xs text-text-tertiary">공유 글을 붙이면 채워져요. 도로명 주소로 적어 주세요.</p>
          </div>
        </div>
        <div className="space-y-4">
          <fieldset>
            <legend className="text-sm font-medium">검색 반경</legend>
            <div className="mt-1.5 flex gap-2">
              {SEARCH_RADII.map((value) => (
                <label
                  key={value}
                  className={`cursor-pointer rounded-full border px-3.5 py-1.5 text-sm font-medium has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-border-brand ${
                    radius === value
                      ? 'border-border-brand bg-bg-brand-soft text-text-brand-strong'
                      : 'border-border-default bg-bg-surface text-text-secondary hover:border-border-strong'
                  }`}
                >
                  <input
                    type="radio"
                    name="org-search-radius"
                    value={value}
                    checked={radius === value}
                    onChange={() => setRadius(value)}
                    className="sr-only"
                  />
                  {formatDistance(value)}
                </label>
              ))}
            </div>
          </fieldset>
          {mapKey && <OfficeMap orgId={org.id} keyId={mapKey} officeAddress={org.officeAddress} />}
          <div>
            <label htmlFor="org-area" className="text-sm font-medium">
              검색 지역(선택)
            </label>
            <input
              id="org-area"
              value={area}
              onChange={(e) => setArea(e.target.value)}
              maxLength={20}
              placeholder="예: 역삼동"
              className={`${inputClass} mt-1.5`}
            />
            <p className="mt-1.5 text-xs text-text-tertiary">「네이버 지도에서 찾기」 검색어 앞에 붙여요.</p>
          </div>
        </div>
      </div>
      {update.error && (
        <p role="alert" className="text-sm text-text-danger">
          {update.error.message}
        </p>
      )}
      <div className="flex justify-end">
        <Button type="submit" disabled={unchanged || update.isPending}>
          저장
        </Button>
      </div>
    </form>
  )
}

/** 저장한 회사 위치 미리보기(Figma 07-L Map Preview). 주소는 볼 때마다 좌표로 바꾼다. */
function OfficeMap({ orgId, keyId, officeAddress }: { orgId: number; keyId: string; officeAddress: string | null }) {
  const places = usePlaces(orgId, officeAddress, [], officeAddress != null)
  const center = places.data?.center
  if (!officeAddress || (places.isSuccess && !center)) {
    return (
      <p className="flex h-40 items-center justify-center rounded-xl bg-bg-muted px-4 text-center text-sm text-text-tertiary">
        {officeAddress ? '저장한 주소를 지도에서 찾지 못했어요' : '회사 주소를 저장하면 지도에 표시돼요'}
      </p>
    )
  }
  if (!center) return <div className="h-40 animate-pulse rounded-xl bg-bg-muted" />
  return (
    <NaverMap
      keyId={keyId}
      className="h-40"
      label="저장한 회사 위치 지도"
      markers={[{ id: 'office', lat: center.lat, lng: center.lng, label: center.name ? `회사 · ${center.name}` : '회사', tone: 'office' }]}
    />
  )
}
