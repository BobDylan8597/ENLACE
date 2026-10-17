import { t } from '../textos/index.js'
import { resumenReporte, tiempoRelativo } from '../util/formato.js'
import Icono from './Icono.jsx'
import InsigniaPrioridad from './InsigniaPrioridad.jsx'

export default function FilaReporte({ reporte, seleccionado, ahora, alElegir }) {
  return (
    <li>
      <button
        type="button"
        onClick={() => alElegir(reporte.id_mensaje)}
        aria-current={seleccionado ? 'true' : undefined}
        className={`flex w-full items-start gap-3 border-b border-gray-200 px-4 py-3 text-left hover:bg-gray-50 ${
          seleccionado ? 'bg-accion-suave' : 'bg-white'
        }`}
      >
        <InsigniaPrioridad valor={reporte.prioridad} />
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-baseline gap-x-2">
            <span className="font-semibold">{reporte.nombre_zona || reporte.id_h3}</span>
            <span className="text-sm text-gray-500">
              {t.tipoReporte[reporte.tipo]} · {t.origen[reporte.origen]} ·{' '}
              {tiempoRelativo(reporte.hora, ahora)}
            </span>
          </span>
          <span className="block truncate text-gray-800">{resumenReporte(reporte)}</span>
          <span className="mt-1 flex flex-wrap gap-2 text-xs">
            <span className="rounded bg-gray-100 px-1.5 py-0.5 text-gray-700">
              {t.estadoReporte[reporte.estado]}
            </span>
            {reporte.actualizado_en && (
              <span className="inline-flex items-center gap-0.5 rounded bg-explorar-suave px-1.5 py-0.5 font-medium text-explorar">
                <Icono nombre="alerta" className="size-3.5" />
                {t.reportes.actualizado}
              </span>
            )}
            {reporte.duplicados > 0 && (
              <span className="rounded bg-gray-100 px-1.5 py-0.5 text-gray-700">
                {t.reportes.duplicados(reporte.duplicados)}
              </span>
            )}
            {reporte.ubicacion && (
              <span className="inline-flex items-center gap-0.5 text-actuar">
                <Icono nombre="ubicacion" className="size-3.5" />
                {t.reportes.puntoExacto}
              </span>
            )}
          </span>
        </span>
      </button>
    </li>
  )
}
