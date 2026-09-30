import { useParams } from 'react-router'

/** 경로의 :orgId. 숫자가 아니면 NaN */
export function useOrgId(): number {
  const { orgId } = useParams()
  return Number(orgId)
}
