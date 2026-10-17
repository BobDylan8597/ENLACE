import { t } from '../textos/index.js'
import { porcentaje } from '../util/formato.js'

// Explica la prioridad: cuánto aporta cada componente (cada uno en 0–1)
export default function BarrasPrioridad({ componentes }) {
  const entradas = Object.entries(componentes ?? {})
  if (!entradas.length) return null
  return (
    <dl className="space-y-1.5">
      {entradas.map(([clave, valor]) => (
        <div key={clave} className="grid grid-cols-[9rem_1fr_3rem] items-center gap-2 text-sm">
          <dt className="text-gray-600">{t.detalle.componentes[clave] ?? clave}</dt>
          <dd className="h-2.5 overflow-hidden rounded-full bg-gray-200">
            <div
              className="h-full rounded-full bg-accion"
              style={{ width: `${Math.round((valor ?? 0) * 100)}%` }}
            />
          </dd>
          <dd className="text-right tabular-nums text-gray-700">{porcentaje(valor)}</dd>
        </div>
      ))}
    </dl>
  )
}
