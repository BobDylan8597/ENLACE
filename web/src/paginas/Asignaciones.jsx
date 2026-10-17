import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import Icono from '../componentes/Icono.jsx'
import EncabezadoPagina from '../componentes/EncabezadoPagina.jsx'
import { t } from '../textos/index.js'
import { tiempoRelativo } from '../util/formato.js'
import { navegar } from '../util/useRutaHash.js'

// A8: seguimiento de las asignaciones en curso (qué equipo va a dónde y desde cuándo)
export default function Asignaciones() {
  const asignaciones = useDatos(() => api.asignaciones(true), [], [
    'asignacion_cambiada',
    'ruta_recalculada',
  ])
  const celdas = useDatos(api.celdas, [], ['evento_emitido'])
  const zonas = Object.fromEntries((celdas.datos ?? []).map((c) => [c.id_h3, c.nombre_zona]))

  return (
    <div className="mx-auto h-full max-w-6xl space-y-5 overflow-y-auto p-5 lg:p-8">
      <EncabezadoPagina titulo={t.asignaciones.titulo} detalle={t.asignaciones.explicacion} />
      {asignaciones.datos?.length ? (
        <ul className="space-y-3">
          {asignaciones.datos.map((a) => (
            <li key={a.id} className="tarjeta p-5">
              <div className="flex flex-wrap items-center gap-3">
                <span className="inline-flex items-center gap-1 rounded-full bg-accion-suave px-2 py-0.5 text-sm font-medium text-accion">
                  <Icono nombre="ruta" className="size-4" />
                  {t.asignaciones.enCamino}
                </span>
                <span className="text-lg font-semibold text-noche">{a.nombre_equipo}</span>
                <span className="text-gray-500">→</span>
                <span className="text-lg">{zonas[a.id_h3] ?? a.id_h3}</span>
                <span className="ml-auto text-sm text-gray-500">
                  {t.asignaciones.decidida(a.decidido_por, tiempoRelativo(a.hora))}
                </span>
              </div>
              <p className="mt-2 text-sm text-slate-600">{a.motivo}</p>
              <div className="mt-2 flex flex-wrap gap-4 text-sm">
                <span>{t.detalle.llegaEn(a.tiempo_estimado_min)}</span>
                {!a.ruta && (
                  <span className="font-medium text-explorar">{t.asignaciones.sinRuta}</span>
                )}
                {a.id_reporte && (
                  <button
                    type="button"
                    className="font-medium text-accion underline"
                    onClick={() => navegar('reportes', a.id_reporte)}
                  >
                    {t.asignaciones.verPedido}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <AvisoEstado
          cargando={asignaciones.cargando}
          error={asignaciones.error}
          vacio={t.asignaciones.vacio}
          alReintentar={asignaciones.recargar}
        />
      )}
    </div>
  )
}
