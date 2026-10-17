import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import Boton from '../componentes/Boton.jsx'
import Icono from '../componentes/Icono.jsx'
import Insignia from '../componentes/Insignia.jsx'
import { t } from '../textos/index.js'
import { lineasInfraestructura } from '../util/formato.js'

const ZONAS_EN_EL_PAQUETE = 10

// A11: paquete de primera hora. Una hoja para imprimir o compartir con los organismos: qué pasó,
// dónde actuar primero, con qué equipos y qué infraestructura hay en esas zonas.
// En producción sería el PDF de GET /paquete/{escenario}; aquí, la misma información imprimible.
export default function PaqueteHora({ ciudad, evento }) {
  const celdas = useDatos(api.celdas, [evento?.id], ['celda_actualizada'])
  const equipos = useDatos(api.equipos, [], ['asignacion_cambiada'])
  const recomendaciones = useDatos(api.recomendaciones, [evento?.id], ['recomendaciones_actualizadas'])
  const detalles = useDatos(
    async () => {
      const primeras = [...(await api.celdas())]
        .sort((a, b) => b.prioridad - a.prioridad)
        .slice(0, ZONAS_EN_EL_PAQUETE)
      return Promise.all(primeras.map((c) => api.celda(c.id_h3)))
    },
    [evento?.id],
    [],
  )

  if (!evento) return <AvisoEstado vacio={t.paquete.sinEvento} />
  if (!detalles.datos || !celdas.datos) {
    return <AvisoEstado cargando={detalles.cargando} error={detalles.error} alReintentar={detalles.recargar} />
  }

  const personas = celdas.datos.reduce((total, c) => total + c.impacto_estimado, 0)
  const criticos = celdas.datos.reduce((total, c) => total + (c.checkins?.criticos ?? 0), 0)
  const disponibles = (equipos.datos ?? []).filter((e) => e.estado === 'disponible')

  return (
    <div className="mx-auto h-full max-w-4xl space-y-6 overflow-y-auto p-5 print:overflow-visible">
      <div className="flex items-start gap-3 print:hidden">
        <p className="flex-1 text-gray-600">{t.paquete.explicacion}</p>
        <Boton variante="principal" onClick={() => window.print()}>
          <Icono nombre="lista" className="size-5" />
          {t.paquete.imprimir}
        </Boton>
      </div>

      <header className="border-b-4 border-actuar pb-3">
        <p className="text-sm font-semibold text-gray-600">
          {t.producto} · {t.paquete.titulo} · {ciudad.nombre}, {ciudad.pais}
        </p>
        <h1 className="text-2xl font-black">
          {t.tipoEvento[evento.tipo]} {evento.magnitud ? `M${evento.magnitud}` : ''} ·{' '}
          {new Date(evento.hora).toLocaleString('es')}
        </h1>
        <p className="mt-1">
          {t.paquete.resumen(Math.round(personas), criticos, evento.zona_afectada.length)}
        </p>
      </header>

      <section>
        <h2 className="mb-2 text-lg font-bold">{t.paquete.zonas}</h2>
        <table className="w-full text-left text-sm">
          <thead className="border-b border-gray-300 text-gray-600">
            <tr>
              <th className="py-1 pr-2">#</th>
              <th className="py-1 pr-2">{t.paquete.zona}</th>
              <th className="py-1 pr-2">{t.paquete.categoria}</th>
              <th className="py-1 pr-2">{t.detalle.prioridad}</th>
              <th className="py-1 pr-2">{t.celda.impacto}</th>
              <th className="py-1">{t.celda.infraestructura}</th>
            </tr>
          </thead>
          <tbody>
            {detalles.datos.map((c, i) => (
              <tr key={c.id_h3} className="border-b border-gray-100 align-top">
                <td className="py-2 pr-2 font-bold">{i + 1}</td>
                <td className="py-2 pr-2 font-medium">{c.nombre_zona}</td>
                <td className="py-2 pr-2">
                  <Insignia categoria={c.categoria} />
                </td>
                <td className="py-2 pr-2 tabular-nums">{Math.round(c.prioridad)}</td>
                <td className="py-2 pr-2 tabular-nums">{Math.round(c.impacto_estimado)}</td>
                <td className="py-2 text-xs">{lineasInfraestructura(c.infraestructura_critica).join(' · ') || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h2 className="mb-2 text-lg font-bold">{t.paquete.primerasAcciones}</h2>
        {recomendaciones.datos?.length ? (
          <ol className="list-inside list-decimal space-y-1 text-sm">
            {recomendaciones.datos.slice(0, 5).map((r) => (
              <li key={r.id}>{r.motivo}</li>
            ))}
          </ol>
        ) : (
          <p className="text-sm text-gray-600">{t.paquete.sinRecomendaciones}</p>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-lg font-bold">{t.paquete.equipos(disponibles.length, equipos.datos?.length ?? 0)}</h2>
        <p className="text-sm">
          {(equipos.datos ?? [])
            .map((e) => `${e.nombre} (${t.estadoEquipo[e.estado].toLowerCase()})`)
            .join(' · ')}
        </p>
      </section>

      <footer className="border-t border-gray-300 pt-2 text-xs text-gray-500">
        {t.paquete.pie(new Date().toLocaleString('es'))}
      </footer>
    </div>
  )
}
