/** Decorative band of circles drawn entirely in CSS. */
export function DotGrid({ rows = 5, className = '' }: { rows?: number; className?: string }) {
  return <div className={`dot-grid ${className}`} style={{ ['--rows' as string]: rows }} aria-hidden="true" />
}
