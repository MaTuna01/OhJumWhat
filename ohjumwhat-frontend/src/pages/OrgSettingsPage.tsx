import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import Button from '../components/Button.tsx'
import InviteLinkField from '../components/InviteLinkField.tsx'
import LeaveOrgDialog from '../components/LeaveOrgDialog.tsx'
import MemberList from '../components/MemberList.tsx'
import { Section } from '../components/PageState.tsx'
import PlaceFields from '../components/PlaceFields.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { serviceLabel } from '../lib/link.ts'
import { type PlaceValue, placeInput } from '../lib/place.ts'
import { columnsClass, inputClass } from '../lib/ui.ts'
import { type Organization, useOrganization, useRenameOrganization, useUpdateOrgLocation } from '../queries/orgs.ts'

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
        <LocationForm key={`${org.area}|${org.officeLink}|${org.officeName}`} org={org} onSaved={() => setLocated(true)} />
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

/** Figma 07-L·D07-L 조직 위치: 검색 지역(「네이버 지도에서 찾기」 검색어 앞에 붙는다)과 회사 위치(지도 링크). 통째로 저장한다. */
function LocationForm({ org, onSaved }: { org: Organization; onSaved: () => void }) {
  const [area, setArea] = useState(org.area ?? '')
  const [office, setOffice] = useState<PlaceValue>({ link: org.officeLink ?? '', name: org.officeName ?? '' })
  const update = useUpdateOrgLocation(org.id)
  const { link: officeLink, placeName: officeName } = placeInput(office)
  const nextArea = area.trim() || null
  const unchanged = nextArea === org.area && officeLink === org.officeLink && officeName === org.officeName

  const submit = (e: FormEvent) => {
    e.preventDefault()
    update.mutate({ area: nextArea, officeLink, officeName }, { onSuccess: onSaved })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-sm text-text-tertiary">「네이버 지도에서 찾기」가 검색 지역을 붙여 근처 식당을 찾아요. 회사 위치는 나중에 결과 지도에 표시해요.</p>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="org-area" className="text-sm font-medium">
            검색 지역
          </label>
          <input
            id="org-area"
            value={area}
            onChange={(e) => setArea(e.target.value)}
            maxLength={20}
            placeholder="예: 역삼동"
            className={`${inputClass} mt-1.5`}
          />
        </div>
        <div className="space-y-1.5">
          <p className="text-sm font-medium">회사 위치(선택)</p>
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
          />
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
