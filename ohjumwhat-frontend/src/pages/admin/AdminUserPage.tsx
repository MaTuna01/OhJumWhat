import { type ReactNode, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { DangerZone, EmptyRow, ListRow, StatCard } from '../../components/AdminParts.tsx'
import AdminProfileModal from '../../components/AdminProfileModal.tsx'
import Avatar from '../../components/Avatar.tsx'
import Badge from '../../components/Badge.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import FoodTags from '../../components/FoodTags.tsx'
import { PageLoader, PageMessage, Section } from '../../components/PageState.tsx'
import ProfileDetailList from '../../components/ProfileDetailList.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { ApiError } from '../../lib/api.ts'
import { formatAgo, formatDate, formatDay, formatDayTime } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import { useAdminUser, useDeleteUserPhoto, useWithdrawUser } from '../../queries/admin.ts'
import { useMe } from '../../queries/me.ts'

/** Figma A03·DA03 회원 상세: 프로필(프로필 수정 A03-M3, 올린 사진 지우기 A03-M2), 활동, 소속 조직, 강제 탈퇴(A03-M) */
export default function AdminUserPage() {
  const userId = Number(useParams().userId)
  const detail = useAdminUser(userId)
  const { data: me } = useMe()
  const withdraw = useWithdrawUser()
  const deletePhoto = useDeleteUserPhoto()
  const navigate = useNavigate()
  const now = useNow(60_000)
  const [confirming, setConfirming] = useState(false)
  const [clearingPhoto, setClearingPhoto] = useState(false)
  const [editing, setEditing] = useState(false)
  useDocumentTitle(detail.data?.user.name, '관리자 콘솔')

  const back = (
    <Link to="/admin/users" className="inline-block text-sm font-medium text-text-tertiary hover:text-text-secondary">
      ‹ 회원 목록
    </Link>
  )
  if (detail.error instanceof ApiError && detail.error.status === 404) {
    return (
      <div className="space-y-4">
        {back}
        <PageMessage title="회원을 찾을 수 없어요">탈퇴했거나 강제 탈퇴된 회원일 수 있어요.</PageMessage>
      </div>
    )
  }
  if (detail.isError) return <PageMessage title="회원 정보를 불러오지 못했어요">{detail.error.message}</PageMessage>
  if (detail.isPending) return <PageLoader />

  const { user, bio, foodTags, details, organizations, activity, lastAccessAt } = detail.data
  const soloOrgs = organizations.filter((o) => o.memberCount <= 1).length
  const isAdmin = user.role === 'ADMIN'

  return (
    <div className="space-y-4">
      {back}
      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0 space-y-4">
          <section className="space-y-4 rounded-2xl border border-border-default bg-bg-surface p-5">
            <div className="flex items-center gap-3">
              <Avatar name={user.name} imageUrl={user.profileImageUrl} size="lg" />
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-2">
                  <span className="truncate text-lg font-bold">{user.name}</span>
                  {isAdmin && <Badge tone="brand">관리자</Badge>}
                </p>
                <p className="truncate text-sm text-text-tertiary">{user.email}</p>
              </div>
              <Button variant="secondary" onClick={() => setEditing(true)} className="shrink-0 py-1.5">
                프로필 수정
              </Button>
            </div>
            <dl className="space-y-2 text-sm">
              {user.googleName !== user.name && <InfoRow label="구글 이름" value={user.googleName} />}
              <InfoRow label="한줄 소개" value={bio ?? '없음'} />
              <InfoRow label="좋아하는 음식" value={foodTags.length > 0 ? <FoodTags tags={foodTags} className="justify-end" /> : '없음'} />
              <InfoRow label="가입" value={formatDate(user.createdAt)} />
              <InfoRow label="최근 로그인" value={user.lastLoginAt ? formatDayTime(user.lastLoginAt, now) : '기록 없음'} />
              <InfoRow label="최근 접속" value={lastAccessAt ? formatAgo(lastAccessAt, now) : '로그인 세션 없음'} />
              <InfoRow
                label="프로필 사진"
                value={
                  user.customPhoto ? (
                    <span className="inline-flex items-center gap-2">
                      올린 사진
                      <button
                        type="button"
                        onClick={() => setClearingPhoto(true)}
                        className="font-medium text-text-danger underline underline-offset-2 hover:no-underline"
                      >
                        지우기
                      </button>
                    </span>
                  ) : (
                    '구글 사진'
                  )
                }
              />
              {!details && <InfoRow label="상세 프로필" value="없음" />}
            </dl>
            {details && <ProfileDetailList details={details} />}
          </section>

          <div className="grid grid-cols-3 gap-3">
            <StatCard label="만든 투표" value={activity.pollsCreated} />
            <StatCard label="올린 메뉴" value={activity.menusAdded} />
            <StatCard label="남긴 응답" value={activity.responses} />
          </div>

          <Section title={`소속 조직 ${organizations.length}개`}>
            <ul className="divide-y divide-border-default">
              {organizations.length === 0 && <EmptyRow>속한 조직이 없어요.</EmptyRow>}
              {organizations.map((o) => (
                <ListRow
                  key={o.id}
                  to={`/admin/orgs/${o.id}`}
                  title={o.name}
                  subtitle={`멤버 ${o.memberCount}명 · ${formatDay(o.joinedAt, now)} 가입`}
                  meta={o.lastVisitedAt ? `${formatDay(o.lastVisitedAt, now)} 방문` : '방문 기록 없음'}
                />
              ))}
            </ul>
          </Section>
        </div>

        <aside className="space-y-4">
          {isAdmin ? (
            <p className="rounded-xl bg-bg-muted px-3.5 py-3 text-xs text-text-secondary">
              관리자는 강제 탈퇴할 수 없어요. 서버 설정(ADMIN_EMAILS)에서 관리자를 해제한 뒤 다시 시도해 주세요.
            </p>
          ) : user.id === me?.id ? null : (
            <DangerZone title="강제 탈퇴" description="모든 조직에서 빼고 이 계정을 삭제해요. 같은 구글 계정으로는 다시 가입할 수 없게 차단돼요.">
              <Button variant="danger" className="w-full" onClick={() => setConfirming(true)}>
                강제 탈퇴
              </Button>
            </DangerZone>
          )}
        </aside>
      </div>

      <AdminProfileModal detail={detail.data} open={editing} onClose={() => setEditing(false)} />

      <ConfirmDialog
        open={clearingPhoto}
        onClose={() => {
          deletePhoto.reset()
          setClearingPhoto(false)
        }}
        onConfirm={() => deletePhoto.mutate(user.id, { onSuccess: () => setClearingPhoto(false) })}
        title="올린 사진을 지울까요?"
        confirmLabel="지우기"
        danger
        pending={deletePhoto.isPending}
        error={deletePhoto.error?.message}
      >
        <p>{user.name} 님이 올린 프로필 사진을 지우고 구글 사진으로 되돌려요. 본인에게 따로 알리지 않아요.</p>
      </ConfirmDialog>

      <ConfirmDialog
        open={confirming}
        onClose={() => {
          withdraw.reset()
          setConfirming(false)
        }}
        onConfirm={() => withdraw.mutate(user.id, { onSuccess: () => navigate('/admin/users', { replace: true }) })}
        title={`${user.name} 님을 강제 탈퇴시킬까요?`}
        confirmLabel="강제 탈퇴"
        danger
        pending={withdraw.isPending}
        error={withdraw.error?.message}
      >
        <p>
          모든 조직에서 빠지고{soloOrgs > 0 && `(혼자 있던 조직 ${soloOrgs}개는 삭제돼요)`} 남긴 응답이 모두 지워져요. 올린 메뉴는 「탈퇴한 사용자」로 남아요.
        </p>
        <p className="mt-3 rounded-lg bg-bg-danger-soft px-3 py-2.5 text-xs text-text-danger">
          같은 구글 계정으로 다시 가입할 수 없게 차단돼요. 「차단」 탭에서 풀 수 있어요.
        </p>
      </ConfirmDialog>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="shrink-0 text-text-tertiary">{label}</dt>
      <dd className="min-w-0 text-right break-words">{value}</dd>
    </div>
  )
}
