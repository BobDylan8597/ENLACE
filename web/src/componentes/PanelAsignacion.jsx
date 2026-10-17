import { useState } from 'react'

import { api, leerOperador } from '../api/cliente.js'
import { t } from '../textos/index.js'
import Boton from './Boton.jsx'
import Icono from './Icono.jsx'

// Equipo sugerido destacado, con "Asignar" como acción principal (sección 16).
// La decisión final siempre es humana: aceptar, cambiar el equipo o rechazar.
export default function PanelAsignacion({ recomendacion, equipos, alDecidir }) {
  const [cambiando, setCambiando] = useState(false)
  const [otroEquipo, setOtroEquipo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  if (!recomendacion) {
    return (
      <p className="rounded-lg bg-sin-datos-suave p-3 text-sin-datos">{t.detalle.sinSugerencia}</p>
    )
  }

  async function decidir(decision, idEquipo) {
    setEnviando(true)
    setError(null)
    try {
      await api.decidir(recomendacion.id, {
        decision,
        decidido_por: leerOperador() ?? 'Operador',
        ...(idEquipo ? { id_equipo: Number(idEquipo) } : {}),
      })
      setCambiando(false)
      alDecidir?.()
    } catch (falla) {
      setError(falla.message)
    } finally {
      setEnviando(false)
    }
  }

  const disponibles = equipos.filter(
    (e) => e.estado === 'disponible' && e.id !== recomendacion.id_equipo,
  )

  return (
    <section className="space-y-3 rounded-xl border-2 border-accion bg-accion-suave p-4">
      <div className="flex items-start gap-3">
        <Icono nombre="equipo" className="mt-0.5 size-6 text-accion" />
        <div>
          <p className="text-sm text-accion">{t.detalle.sugerido}</p>
          <p className="text-lg font-semibold">{recomendacion.nombre_equipo}</p>
          <p className="font-medium text-accion">
            {t.detalle.llegaEn(recomendacion.tiempo_estimado_min)}
          </p>
        </div>
      </div>
      <p className="text-sm text-gray-700">{recomendacion.motivo}</p>
      {error && (
        <p className="text-sm text-actuar" role="alert">
          {error}
        </p>
      )}
      {cambiando ? (
        <div className="space-y-2">
          <label className="block text-sm">
            {t.detalle.elegirEquipo}
            <select
              value={otroEquipo}
              onChange={(e) => setOtroEquipo(e.target.value)}
              className="mt-1 block min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3"
            >
              <option value="" disabled>
                —
              </option>
              {disponibles.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.nombre} ({e.capacidades.map((c) => t.capacidad[c]).join(', ')})
                </option>
              ))}
            </select>
          </label>
          <div className="flex flex-wrap gap-2">
            <Boton
              variante="principal"
              disabled={!otroEquipo || enviando}
              onClick={() => decidir('modificada', otroEquipo)}
            >
              {t.detalle.confirmarCambio}
            </Boton>
            <Boton onClick={() => setCambiando(false)}>{t.alerta.volver}</Boton>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <Boton
            variante="principal"
            className="flex-1"
            disabled={enviando}
            onClick={() => decidir('aceptada')}
          >
            {t.detalle.asignar}
          </Boton>
          <Boton disabled={enviando || !disponibles.length} onClick={() => setCambiando(true)}>
            {t.detalle.cambiarEquipo}
          </Boton>
          <Boton variante="texto" disabled={enviando} onClick={() => decidir('rechazada')}>
            {t.detalle.rechazar}
          </Boton>
        </div>
      )}
    </section>
  )
}
