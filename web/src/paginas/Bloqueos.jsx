import { useState } from 'react'

import { api, leerOperador } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import Boton from '../componentes/Boton.jsx'
import Icono from '../componentes/Icono.jsx'
import { t } from '../textos/index.js'
import { estiloBloqueo } from '../util/colores.js'
import { tiempoRelativo } from '../util/formato.js'

// A10: bloqueos reportados. Un operador puede confirmarlos o descartarlos (con su clave)
export default function Bloqueos() {
  const bloqueos = useDatos(api.bloqueos, [], ['bloqueo_cambiado'])
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(null)

  async function decidir(bloqueo, accion) {
    setOcupado(bloqueo.id)
    setError(null)
    try {
      await api.decidirBloqueo({
        accion,
        id_bloqueo: bloqueo.id,
        reportante: (leerOperador() ?? 'operador').slice(0, 36).padEnd(3, '_'),
        origen: 'operador',
      })
      bloqueos.recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupado(null)
    }
  }

  const activos = (bloqueos.datos ?? []).filter((b) => b.estado !== 'descartado')
  return (
    <div className="mx-auto h-full max-w-5xl space-y-6 overflow-y-auto p-5">
      <h1 className="text-xl font-bold">{t.bloqueos.titulo}</h1>
      <p className="text-gray-600">{t.bloqueos.explicacion}</p>
      {error && <p className="font-medium text-actuar">{error}</p>}
      {activos.length ? (
        <ul className="space-y-3">
          {activos.map((b) => {
            const estilo = estiloBloqueo(b.estado)
            return (
              <li key={b.id} className="flex flex-wrap items-center gap-3 rounded-xl bg-white p-4 shadow-sm">
                <span
                  className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-sm font-medium ${estilo.clase}`}
                >
                  <Icono nombre="bloqueo" className="size-4" />
                  {t.estadoBloqueo[b.estado]}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">
                    {t.tipoBloqueo[b.tipo]}
                    {b.alcance === 'solo_a_pie' && ` · ${t.bloqueos.aPie}`}
                  </p>
                  <p className="text-sm text-gray-600">
                    {t.bloqueos.reportes(b.confirmaciones)}
                    {b.confirmado_por_brigada && ` · ${t.bloqueos.porBrigada}`} ·{' '}
                    {tiempoRelativo(b.actualizado)}
                    {!b.afecta_rutas && ` · ${t.bloqueos.sinCalle}`}
                  </p>
                </div>
                {b.estado !== 'confirmado' && (
                  <Boton variante="principal" disabled={ocupado === b.id} onClick={() => decidir(b, 'confirmar')}>
                    {t.bloqueos.confirmar}
                  </Boton>
                )}
                <Boton disabled={ocupado === b.id} onClick={() => decidir(b, 'descartar')}>
                  {t.bloqueos.descartar}
                </Boton>
              </li>
            )
          })}
        </ul>
      ) : (
        <AvisoEstado
          cargando={bloqueos.cargando}
          error={bloqueos.error}
          vacio={t.bloqueos.vacio}
          alReintentar={bloqueos.recargar}
        />
      )}
    </div>
  )
}
