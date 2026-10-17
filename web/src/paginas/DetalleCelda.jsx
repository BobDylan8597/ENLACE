import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import BarrasPrioridad from '../componentes/BarrasPrioridad.jsx'
import Boton from '../componentes/Boton.jsx'
import FilaReporte from '../componentes/FilaReporte.jsx'
import Icono from '../componentes/Icono.jsx'
import Insignia from '../componentes/Insignia.jsx'
import InsigniaPrioridad from '../componentes/InsigniaPrioridad.jsx'
import { t } from '../textos/index.js'
import { entero, lineasInfraestructura, porcentaje, tiempoRelativo } from '../util/formato.js'
import { navegar } from '../util/useRutaHash.js'

// A5: qué se sabe de una zona y con cuánta certeza
export default function DetalleCelda({ idH3 }) {
  const celda = useDatos(() => api.celda(idH3), [idH3], ['celda_actualizada'])
  const reportes = useDatos(() => api.reportes({ id_h3: idH3 }), [idH3], [
    'reporte_recibido',
    'asignacion_cambiada',
  ])

  if (!celda.datos) {
    return <AvisoEstado cargando={celda.cargando} error={celda.error} alReintentar={celda.recargar} />
  }
  const c = celda.datos
  const datos = [
    [t.celda.impacto, entero(c.impacto_estimado)],
    [t.celda.inicial, entero(c.impacto_inicial)],
    [t.celda.certeza, porcentaje(c.certeza)],
    [t.celda.vulnerabilidad, porcentaje(c.vulnerabilidad)],
    [t.celda.reportes, c.reportes],
    [t.celda.checkins, t.celda.totales(c.checkins)],
    [t.celda.personas, c.checkins.personas],
    [t.celda.vulnerables, c.checkins.vulnerables],
    [t.celda.criticos, c.checkins.criticos],
    [t.celda.ultimaAtencion, c.ultima_atencion ? tiempoRelativo(c.ultima_atencion) : t.celda.nunca],
  ]
  const infraestructura = lineasInfraestructura(c.infraestructura_critica)

  return (
    <article className="space-y-4 p-5">
      <header className="flex items-start gap-3">
        <InsigniaPrioridad valor={c.prioridad} grande />
        <div className="min-w-0 flex-1">
          <p className="text-sm text-gray-500">{t.celda.titulo}</p>
          <h2 className="text-xl font-bold">{c.nombre_zona}</h2>
          <Insignia categoria={c.categoria} />
        </div>
        <Boton variante="texto" onClick={() => navegar('mapa')} aria-label={t.detalle.cerrar}>
          <Icono nombre="cerrar" />
        </Boton>
      </header>

      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 text-sm">
        {datos.map(([etiqueta, valor]) => (
          <div key={etiqueta} className="contents">
            <dt className="text-gray-600">{etiqueta}</dt>
            <dd className="text-right font-medium tabular-nums">{valor}</dd>
          </div>
        ))}
      </dl>

      {infraestructura.length > 0 && (
        <section className="text-sm">
          <h3 className="mb-1 font-semibold">{t.celda.infraestructura}</h3>
          <ul className="list-inside list-disc space-y-0.5">
            {infraestructura.map((linea) => (
              <li key={linea}>{linea}</li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h3 className="mb-2 font-semibold">{t.detalle.porQue}</h3>
        <BarrasPrioridad componentes={c.componentes_prioridad} />
      </section>

      {reportes.datos?.length > 0 && (
        <section>
          <h3 className="mb-1 font-semibold">{t.celda.reportes}</h3>
          <ul className="overflow-hidden rounded-lg border border-gray-200">
            {reportes.datos.map((r) => (
              <FilaReporte key={r.id_mensaje} reporte={r} alElegir={(id) => navegar('reportes', id)} />
            ))}
          </ul>
        </section>
      )}
    </article>
  )
}
