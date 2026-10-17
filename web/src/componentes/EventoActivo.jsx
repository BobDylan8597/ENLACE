import { useState } from 'react'

import { api } from '../api/cliente.js'
import { t } from '../textos/index.js'
import { tiempoRelativo } from '../util/formato.js'
import Boton from './Boton.jsx'
import Icono from './Icono.jsx'

// Evento en curso: el único paso posible es cerrarlo (inicia el plazo de borrado)
export default function EventoActivo({ evento, alCerrar }) {
  const [confirmando, setConfirmando] = useState(false)
  const [error, setError] = useState(null)

  async function cerrar() {
    try {
      await api.cerrarEvento(evento.id)
      alCerrar()
    } catch (falla) {
      setError(falla.message)
    }
  }

  return (
    <section className="space-y-3 rounded-xl border-2 border-actuar bg-actuar-suave p-5">
      <h2 className="flex items-center gap-2 text-lg font-bold text-actuar">
        <Icono nombre="alerta" />
        {t.evento.activo(t.tipoEvento[evento.tipo], evento.magnitud)}
      </h2>
      <p className="text-gray-700">
        {tiempoRelativo(evento.hora)} · {evento.zona_afectada.length} zonas
      </p>
      {error && <p className="text-actuar">{error}</p>}
      {confirmando ? (
        <div className="space-y-2 rounded-lg bg-white p-3">
          <p>{t.alerta.confirmarCierre}</p>
          <div className="flex gap-2">
            <Boton variante="peligro" onClick={cerrar}>
              {t.alerta.cerrarAhora}
            </Boton>
            <Boton onClick={() => setConfirmando(false)}>{t.alerta.volver}</Boton>
          </div>
        </div>
      ) : (
        <Boton onClick={() => setConfirmando(true)}>{t.alerta.cerrarEvento}</Boton>
      )}
    </section>
  )
}
