import { assetPath } from '../app/routing'

export function OpenClawIcon({ size = 31 }: { size?: number | string }) {
  return (
    <img
      className="professional-stack-logo"
      src={assetPath('openclaw.svg')}
      alt=""
      width={size}
      height={size}
    />
  )
}
