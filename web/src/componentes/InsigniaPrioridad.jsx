import { t } from '../textos/index.js'
import { estiloPrioridad } from '../util/colores.js'
import Icono from './Icono.jsx'

export default function InsigniaPrioridad({ valor, grande = false }) {
  const estilo = estiloPrioridad(valor)
  const tamano = grande ? 'text-2xl px-3 py-1.5' : 'text-base px-2 py-1'
  return (
    <span
      className={`inline-flex min-w-16 items-center justify-center gap-1 rounded-lg font-bold tabular-nums ${tamano} ${estilo.clase}`}
      title={`${t.detalle.prioridad} ${Math.round(valor)}/100`}
    >
      <Icono nombre={estilo.icono} className={grande ? 'size-6' : 'size-4'} />
      {Math.round(valor)}
    </span>
  )
}
