import { useState } from 'react'

import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import { t } from '../textos/index.js'
import { tiempoRelativo } from '../util/formato.js'
import AvisoEstado from './AvisoEstado.jsx'
import Boton from './Boton.jsx'
import Icono from './Icono.jsx'

// Propuestas de la comunidad: la alerta se emite solo si un operador la aprueba
export default function ListaPropuestas({ hayEvento, alAprobar }) {
  const propuestas = useDatos(api.propuestas, [], ['propuesta_comunitaria', 'evento_emitido'])
  const [error, setError] = useState(null)

  async function actuar(accion) {
    setError(null)
    try {
      await accion()
      propuestas.recargar()
    } catch (falla) {
      setError(falla.message)
    }
  }

  return (
    <section className="space-y-2">
      <h2 className="font-semibold">{t.alerta.propuestas}</h2>
      {error && <p className="text-sm text-actuar">{error}</p>}
      {propuestas.datos?.length ? (
        <ul className="space-y-2">
          {propuestas.datos.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center gap-2 rounded-lg bg-explorar-suave p-3">
              <Icono nombre="persona" className="size-5 text-explorar" />
              <span className="flex-1">
                {t.alerta.propuesta(p.pedidos, p.id_h3)} · {tiempoRelativo(p.hora)}
              </span>
              <Boton
                variante="peligro"
                disabled={hayEvento}
                onClick={() =>
                  actuar(async () => {
                    await api.emitir({ fuente: 'comunitaria', id_propuesta: p.id })
                    alAprobar()
                  })
                }
              >
                {t.alerta.aprobar}
              </Boton>
              <Boton onClick={() => actuar(() => api.descartarPropuesta(p.id))}>
                {t.alerta.descartar}
              </Boton>
            </li>
          ))}
        </ul>
      ) : (
        <AvisoEstado cargando={propuestas.cargando} vacio={t.alerta.sinPropuestas} />
      )}
    </section>
  )
}
