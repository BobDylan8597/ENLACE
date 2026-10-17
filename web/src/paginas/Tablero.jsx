import { useCallback, useEffect, useState } from 'react'

import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import Insignia from '../componentes/Insignia.jsx'
import MapaBase from '../componentes/MapaBase.jsx'
import { t } from '../textos/index.js'
import {
  capaBloqueos,
  capaCeldas,
  capaCheckins,
  capaEquipos,
  capaPedidosDeAyuda,
  capaPersonas,
  capaPoblacionCritica,
  capaRutas,
  estiloPersona,
} from '../util/capasMapa.js'
import { navegar } from '../util/useRutaHash.js'
import DetalleCelda from './DetalleCelda.jsx'

const CAPAS = ['celdas', 'criticos', 'personas', 'checkins', 'ayuda', 'equipos', 'bloqueos', 'rutas']
// Las capas más detalladas empiezan apagadas para no tapar las zonas
const APAGADAS = ['checkins']

// Muestra una capa en el mapa mientras esté activa y sus datos existan
function useCapa(mapa, visible, datos, crear) {
  useEffect(() => {
    if (!mapa || !visible || !datos) return
    const capa = crear(datos).addTo(mapa)
    return () => capa.remove()
  }, [mapa, visible, datos, crear])
}

const elegirCelda = (id) => navegar('mapa', id)
const elegirReporte = (id) => navegar('reportes', id)
const crearAyuda = (datos) => capaPedidosDeAyuda(datos, elegirReporte)
const crearCriticos = (datos) => capaPoblacionCritica(datos, elegirCelda)

// A4: tablero con mapa por capas
export default function Tablero({ ciudad, evento, parametro }) {
  const [mapa, setMapa] = useState(null)
  const [visibles, setVisibles] = useState(
    Object.fromEntries(CAPAS.map((c) => [c, !APAGADAS.includes(c)])),
  )

  const celdas = useDatos(api.celdas, [evento?.id], ['celda_actualizada', 'evento_emitido'])
  const ayuda = useDatos(() => api.reportes({ tipo: 'ayuda' }), [evento?.id], [
    'reporte_recibido',
    'asignacion_cambiada',
  ])
  const personas = useDatos(api.ubicaciones, [evento?.id], [
    'checkin_recibido',
    'evento_emitido',
    'evento_cerrado',
  ])
  const equipos = useDatos(api.equipos, [], ['asignacion_cambiada'])
  const bloqueos = useDatos(api.bloqueos, [], ['bloqueo_cambiado'])
  const rutas = useDatos(() => api.asignaciones(true), [], [
    'asignacion_cambiada',
    'ruta_recalculada',
  ])

  const crearCeldas = useCallback((datos) => capaCeldas(datos, elegirCelda, parametro), [parametro])

  // Sin evento, las zonas no tienen estimación: no se dibujan
  useCapa(mapa, visibles.celdas && evento, celdas.datos, crearCeldas)
  useCapa(mapa, visibles.criticos && evento, celdas.datos, crearCriticos)
  useCapa(mapa, visibles.checkins && evento, celdas.datos, capaCheckins)
  useCapa(mapa, visibles.personas && evento, personas.datos, capaPersonas)
  useCapa(mapa, visibles.ayuda, ayuda.datos, crearAyuda)
  useCapa(mapa, visibles.bloqueos, bloqueos.datos, capaBloqueos)
  useCapa(mapa, visibles.rutas, rutas.datos, capaRutas)
  useCapa(mapa, visibles.equipos, equipos.datos, capaEquipos)

  return (
    <div className="grid h-full min-h-0 grid-cols-1 lg:grid-cols-[1fr_minmax(22rem,28rem)]">
      <div className="relative min-h-96">
        <MapaBase ciudad={ciudad} alListo={setMapa} />
        {!ciudad.mapa_disponible && (
          <p className="absolute top-3 left-14 z-[500] rounded bg-explorar-suave px-3 py-2 text-sm text-explorar">
            {t.tablero.sinMapa}
          </p>
        )}
      </div>
      <aside className="min-h-0 overflow-y-auto border-l border-slate-200 bg-white shadow-[-4px_0_12px_rgba(15,42,61,0.04)]">
        {parametro ? (
          <DetalleCelda idH3={parametro} />
        ) : (
          <div className="space-y-5 p-5">
            <h1 className="text-xl font-bold tracking-tight text-noche">{t.tablero.titulo}</h1>
            <fieldset>
              <legend className="mb-2 font-semibold">{t.tablero.capas}</legend>
              {CAPAS.map((capa) => (
                <label key={capa} className="flex min-h-11 items-center gap-3">
                  <input
                    type="checkbox"
                    className="size-5"
                    checked={visibles[capa]}
                    onChange={(e) => setVisibles({ ...visibles, [capa]: e.target.checked })}
                  />
                  {t.tablero.capa[capa]}
                </label>
              ))}
            </fieldset>
            <section>
              <h2 className="mb-2 font-semibold">{t.tablero.leyenda}</h2>
              <ul className="space-y-2">
                {['actuar', 'explorar', 'estable', 'explorar_despues'].map((categoria) => (
                  <li key={categoria}>
                    <Insignia categoria={categoria} />
                  </li>
                ))}
              </ul>
              <h3 className="mt-4 mb-2 text-sm font-semibold">{t.tablero.capa.personas}</h3>
              <ul className="space-y-2 text-sm">
                {['sin_respuesta', 'ayuda', 'bien'].map((estado) => (
                  <li key={estado} className="flex items-center gap-2">
                    <span
                      className="marca-persona"
                      style={{ background: estiloPersona(estado).estilo.color }}
                      aria-hidden="true"
                    >
                      {estiloPersona(estado).simbolo}
                    </span>
                    {t.estadoPersona[estado]}
                  </li>
                ))}
                <li className="flex items-center gap-2">
                  <span className="marca-persona marca-persona--vulnerable bg-gray-400" aria-hidden="true" />
                  {t.tablero.conVulnerables}
                </li>
              </ul>
              <p className="mt-2 text-xs text-gray-500">{t.tablero.privacidadPersonas}</p>
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}
