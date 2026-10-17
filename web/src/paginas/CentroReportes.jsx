import { useEffect, useState } from 'react'

import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import FilaReporte from '../componentes/FilaReporte.jsx'
import { REFRESCO_RELOJ_MS } from '../configuracion.js'
import { t } from '../textos/index.js'
import { navegar } from '../util/useRutaHash.js'
import DetalleReporte from './DetalleReporte.jsx'

const FILTROS = [
  ['estado', ['sin_asignar', 'asignado', 'atendido'], 'estadoReporte'],
  ['tipo', ['ayuda', 'evaluacion', 'bien'], 'tipoReporte'],
  ['origen', ['app', 'sms', 'nearby', 'radio', 'brigada'], 'origen'],
]

// A2: vista inicial del administrador. Bandeja ordenada por prioridad, siempre al día.
export default function CentroReportes({ evento, seleccionado }) {
  const [filtros, setFiltros] = useState({ estado: 'sin_asignar' })
  const [ahora, setAhora] = useState(Date.now())
  const clave = JSON.stringify(filtros)
  const bandeja = useDatos(() => api.reportes(filtros), [clave, evento?.id], [
    'reporte_recibido',
    'asignacion_cambiada',
    'celda_actualizada',
  ])

  useEffect(() => {
    const reloj = setInterval(() => setAhora(Date.now()), REFRESCO_RELOJ_MS)
    return () => clearInterval(reloj)
  }, [])

  return (
    <div className="grid h-full min-h-0 grid-cols-1 lg:grid-cols-[minmax(24rem,1fr)_minmax(26rem,32rem)]">
      <section className="flex min-h-0 flex-col border-r border-gray-200">
        <div className="flex flex-wrap items-end gap-3 border-b border-gray-200 bg-white px-4 py-3">
          <h1 className="mr-auto text-lg font-bold">
            {t.reportes.titulo}
            {bandeja.datos && <span className="ml-2 text-gray-500"> ({bandeja.datos.length})</span>}
          </h1>
          {FILTROS.map(([campo, valores, textos]) => (
            <label key={campo} className="text-sm">
              <span className="block text-gray-600">{t.reportes.filtros[campo]}</span>
              <select
                value={filtros[campo] ?? ''}
                onChange={(e) => setFiltros({ ...filtros, [campo]: e.target.value || undefined })}
                className="min-h-11 rounded-lg border border-gray-300 bg-white px-2"
              >
                <option value="">{t.reportes.filtros.todos}</option>
                {valores.map((v) => (
                  <option key={v} value={v}>
                    {t[textos][v]}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {!evento ? (
            <AvisoEstado vacio={t.reportes.sinEvento} />
          ) : bandeja.datos?.length ? (
            <ul>
              {bandeja.datos.map((reporte) => (
                <FilaReporte
                  key={reporte.id_mensaje}
                  reporte={reporte}
                  ahora={ahora}
                  seleccionado={reporte.id_mensaje === seleccionado}
                  alElegir={(id) => navegar('reportes', id)}
                />
              ))}
            </ul>
          ) : (
            <AvisoEstado
              cargando={bandeja.cargando && !bandeja.datos}
              error={bandeja.error}
              vacio={t.reportes.vacio}
              alReintentar={bandeja.recargar}
            />
          )}
        </div>
      </section>
      <aside className="min-h-0 overflow-y-auto bg-white">
        {seleccionado ? (
          <DetalleReporte id={seleccionado} />
        ) : (
          <AvisoEstado vacio={t.reportes.seleccionar} />
        )}
      </aside>
    </div>
  )
}
