import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import BarrasPrioridad from '../componentes/BarrasPrioridad.jsx'
import Boton from '../componentes/Boton.jsx'
import Icono from '../componentes/Icono.jsx'
import InsigniaPrioridad from '../componentes/InsigniaPrioridad.jsx'
import PanelAsignacion from '../componentes/PanelAsignacion.jsx'
import { t } from '../textos/index.js'
import { distancia, resumenReporte, tiempoRelativo } from '../util/formato.js'
import { navegar } from '../util/useRutaHash.js'

const ACTUALIZAR_CON = ['reporte_recibido', 'asignacion_cambiada', 'recomendaciones_actualizadas']

// A3: detalle del reporte, por qué tiene esa prioridad y a quién asignarlo
export default function DetalleReporte({ id }) {
  const reporte = useDatos(() => api.reporte(id), [id], ACTUALIZAR_CON)
  const recomendaciones = useDatos(api.recomendaciones, [], ACTUALIZAR_CON)
  const equipos = useDatos(api.equipos, [], ['asignacion_cambiada'])

  if (!reporte.datos) {
    return <AvisoEstado cargando={reporte.cargando} error={reporte.error} alReintentar={reporte.recargar} />
  }
  const r = reporte.datos
  const recomendacion = recomendaciones.datos?.find((a) => a.id_reporte === r.id_mensaje)
  const enCurso = r.asignaciones.find((a) => ['aceptada', 'modificada'].includes(a.decision) && !a.hora_fin)

  async function marcarAtendido() {
    await api.marcarAtendida(enCurso.id)
    reporte.recargar()
  }

  return (
    <article className="space-y-5 p-5">
      <header className="flex items-start gap-3">
        <InsigniaPrioridad valor={r.prioridad} grande />
        <div className="min-w-0 flex-1">
          <h2 className="text-xl font-bold">{r.nombre_zona || r.id_h3}</h2>
          <p className="text-gray-600">
            {t.tipoReporte[r.tipo]} · {t.origen[r.origen]} · {tiempoRelativo(r.hora)} ·{' '}
            {t.estadoReporte[r.estado]}
          </p>
        </div>
        <Boton variante="texto" onClick={() => navegar('reportes')} aria-label={t.detalle.cerrar}>
          <Icono nombre="cerrar" />
        </Boton>
      </header>

      <p className="text-lg">{resumenReporte(r)}</p>

      {r.tipo === 'ayuda' && (
        <section className="rounded-lg bg-gray-100 p-3">
          <h3 className="mb-1 flex items-center gap-1 font-semibold">
            <Icono nombre="persona" className="size-4" />
            {t.detalle.persona}
          </h3>
          {r.identidad ? (
            <dl className="grid grid-cols-[7rem_1fr] gap-x-2 text-sm">
              {r.identidad.nombre && (
                <>
                  <dt className="text-gray-600">{t.detalle.nombre}</dt>
                  <dd>{r.identidad.nombre}</dd>
                </>
              )}
              {r.identidad.telefono && (
                <>
                  <dt className="text-gray-600">{t.detalle.telefono}</dt>
                  <dd>{r.identidad.telefono}</dd>
                </>
              )}
              {r.identidad.condiciones && (
                <>
                  <dt className="text-gray-600">{t.detalle.condiciones}</dt>
                  <dd>{r.identidad.condiciones}</dd>
                </>
              )}
            </dl>
          ) : (
            <p className="text-sm text-gray-600">{t.detalle.sinDatosPersonales}</p>
          )}
          {r.ubicacion && (
            <p className="mt-1 flex items-center gap-1 text-sm text-actuar">
              <Icono nombre="ubicacion" className="size-4" />
              {t.reportes.puntoExacto}: {r.ubicacion.lat.toFixed(5)}, {r.ubicacion.lon.toFixed(5)}
            </p>
          )}
        </section>
      )}

      {r.estado === 'sin_asignar' && r.tipo === 'ayuda' && (
        <PanelAsignacion
          recomendacion={recomendacion}
          equipos={equipos.datos ?? []}
          alDecidir={reporte.recargar}
        />
      )}

      {enCurso && (
        <section className="space-y-2 rounded-xl border border-accion p-4">
          <p className="font-semibold text-accion">
            {enCurso.nombre_equipo} · {t.detalle.llegaEn(enCurso.tiempo_estimado_min)}
          </p>
          {enCurso.ruta && (
            <div className="text-sm">
              <p className="font-medium">
                {t.detalle.ruta}: {distancia(enCurso.ruta.properties.distancia_m)}
              </p>
              <ol className="ml-5 list-decimal text-gray-700">
                {enCurso.ruta.properties.instrucciones.slice(0, 6).map((paso, i) => (
                  <li key={i}>{paso.texto}</li>
                ))}
              </ol>
            </div>
          )}
          <Boton variante="principal" onClick={marcarAtendido}>
            {t.detalle.marcarAtendido}
          </Boton>
        </section>
      )}

      <section>
        <h3 className="mb-2 font-semibold">{t.detalle.porQue}</h3>
        <BarrasPrioridad componentes={r.componentes_prioridad} />
      </section>

      {r.asignaciones.length > 0 && (
        <section>
          <h3 className="mb-2 font-semibold">{t.detalle.historial}</h3>
          <ul className="space-y-1 text-sm">
            {r.asignaciones.map((a) => (
              <li key={a.id} className="rounded bg-gray-100 px-2 py-1">
                {a.nombre_equipo} · {t.decision[a.decision]}
                {a.decidido_por && ` · ${t.detalle.decididoPor} ${a.decidido_por}`}
              </li>
            ))}
          </ul>
        </section>
      )}
    </article>
  )
}
