import type { SignStatus } from '../api/types'
import { STATUS_HELP, STATUS_LABEL } from '../status'

export function StatusBadge({ status }: { status: SignStatus }) {
  return (
    <span className={`badge badge--${status.toLowerCase().replace(/_/g, '-')}`} title={STATUS_HELP[status]}>
      {STATUS_LABEL[status]}
    </span>
  )
}
