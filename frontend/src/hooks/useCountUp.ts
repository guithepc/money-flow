import { useEffect, useRef, useState } from 'react'

/**
 * Anima a transição entre valores numéricos via requestAnimationFrame.
 * Usado no saldo do card ao trocar de conta — dá a sensação "smooth" pedida.
 */
export function useCountUp(target: number, duration = 500): number {
  const [value, setValue] = useState(target)
  const fromRef = useRef(target)
  const rafRef = useRef<number>(0)

  useEffect(() => {
    const from = fromRef.current
    const delta = target - from
    if (delta === 0) return

    const start = performance.now()
    // easeOutCubic — desacelera no fim, sensação natural.
    const ease = (t: number) => 1 - Math.pow(1 - t, 3)

    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1)
      setValue(from + delta * ease(progress))
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick)
      } else {
        fromRef.current = target
      }
    }

    rafRef.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(rafRef.current)
  }, [target, duration])

  return value
}
