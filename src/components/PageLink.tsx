import type { AnchorHTMLAttributes, ReactNode } from 'react'
import type { Route } from '../app/routing'
import { withBasePath } from '../app/routing'

export function PageLink({
  href,
  onNavigate,
  active,
  children,
  className,
  ...props
}: {
  href: Route
  onNavigate: (path: Route) => void
  active?: boolean
  children: ReactNode
  className?: string
} & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href' | 'onClick'>) {
  return (
    <a
      {...props}
      aria-current={active ? 'page' : undefined}
      className={className}
      href={withBasePath(href)}
      onClick={(event) => {
        event.preventDefault()
        onNavigate(href)
      }}
    >
      {children}
    </a>
  )
}
