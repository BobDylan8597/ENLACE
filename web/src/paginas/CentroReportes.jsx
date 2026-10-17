import { useEffect, useState } from 'react'

import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import EncabezadoPagina from '../componentes/EncabezadoPagina.jsx'
import FilaReporte from '../componentes/FilaReporte.jsx'
import Indicadores from '../componentes/Indicadores.jsx'
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
    'reporte_actualizado',
    'asignacion_cambiada',
    'celda_actualizada',
  ])

  useEffect(() => {
    const reloj = setInterval(() => setAhora(Date.now()), REFRESCO_RELOJ_MS)
    return () => clearInterval(reloj)
  }, [])

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 overflow-y-auto p-5 lg:overflow-hidden">
      <EncabezadoPagina titulo={t.reportes.titulo} detalle={t.reportes.detalle} />
      <Indicadores evento={evento} />
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 lg:grid-cols-[minmax(24rem,1fr)_minmax(26rem,32rem)]">
      <section className="tarjeta flex min-h-[28rem] flex-col overflow-hidden lg:min-h-0">
        <div className="flex flex-wrap items-end gap-3 border-b border-slate-200 px-5 py-3">
          <h2 className="mr-auto text-lg font-semibold text-noche">
            {t.reportes.bandeja}
            {bandeja.datos && (
              <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-sm font-semibold text-slate-600">
                {bandeja.datos.length}
              </span>
            )}
          </h2>
          {FILTROS.map(([campo, valores, textos]) => (
            <label key={campo} className="text-sm">
              <span className="block text-xs font-semibold text-slate-500">{t.reportes.filtros[campo]}</span>
              <select
                value={filtros[campo] ?? ''}
                onChange={(e) => setFiltros({ ...filtros, [campo]: e.target.value || undefined })}
                className="mt-1 min-h-10 rounded-xl border border-slate-300 bg-white px-2 focus:border-marca focus:ring-4 focus:ring-marca/20 focus:outline-none"
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
      <aside className="tarjeta min-h-0 overflow-y-auto">
        {seleccionado ? (
          <DetalleReporte id={seleccionado} />
        ) : (
          <AvisoEstado vacio={t.reportes.seleccionar} />
        )}
      </aside>
      </div>
    </div>
  )
}
