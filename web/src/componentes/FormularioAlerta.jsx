import { useCallback, useState } from 'react'

import { api } from '../api/cliente.js'
import { t } from '../textos/index.js'
import Boton from './Boton.jsx'
import CampoFormulario, { ENTRADA } from './CampoFormulario.jsx'
import Icono from './Icono.jsx'
import SelectorUbicacion from './SelectorUbicacion.jsx'

const TIPOS = ['sismo', 'inundacion', 'huracan', 'otro']

// Alerta manual: se revisa y se confirma antes de emitir (no se puede deshacer)
export default function FormularioAlerta({ ciudad, alEmitir }) {
  const [tipo, setTipo] = useState('sismo')
  const [magnitud, setMagnitud] = useState('')
  const [profundidad, setProfundidad] = useState('')
  const [ubicacion, setUbicacion] = useState({ lat: ciudad.centro.lat, lon: ciudad.centro.lon })
  const [revisando, setRevisando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const cambiarUbicacion = useCallback(setUbicacion, [])

  async function emitir() {
    setEnviando(true)
    setError(null)
    try {
      await api.emitir({
        fuente: 'manual',
        tipo,
        lat: ubicacion.lat,
        lon: ubicacion.lon,
        ...(magnitud ? { magnitud: Number(magnitud) } : {}),
        ...(profundidad ? { profundidad_km: Number(profundidad) } : {}),
      })
      alEmitir()
    } catch (falla) {
      setError(falla.message)
      setRevisando(false)
    } finally {
      setEnviando(false)
    }
  }

  if (revisando) {
    return (
      <section className="space-y-3 rounded-xl border-2 border-actuar bg-white p-5">
        <h2 className="text-lg font-bold">{t.alerta.confirmarTitulo}</h2>
        <p className="text-xl font-semibold text-actuar">
          {t.tipoEvento[tipo]}
          {magnitud && ` M${magnitud}`}
          {profundidad && ` · ${profundidad} km`}
        </p>
        <p className="text-gray-700">
          {ubicacion.lat.toFixed(4)}, {ubicacion.lon.toFixed(4)}
        </p>
        <p>{t.alerta.confirmarTexto}</p>
        <div className="flex flex-wrap gap-2">
          <Boton variante="peligro" disabled={enviando} onClick={emitir}>
            <Icono nombre="alerta" />
            {t.alerta.emitir}
          </Boton>
          <Boton onClick={() => setRevisando(false)}>{t.alerta.volver}</Boton>
        </div>
      </section>
    )
  }

  return (
    <form
      className="space-y-4 rounded-xl bg-white p-5 shadow-sm"
      onSubmit={(e) => {
        e.preventDefault()
        setRevisando(true)
      }}
    >
      {error && (
        <p className="text-actuar" role="alert">
          {error}
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-3">
        <CampoFormulario etiqueta={t.alerta.tipo}>
          <select value={tipo} onChange={(e) => setTipo(e.target.value)} className={ENTRADA}>
            {TIPOS.map((valor) => (
              <option key={valor} value={valor}>
                {t.tipoEvento[valor]}
              </option>
            ))}
          </select>
        </CampoFormulario>
        <CampoFormulario etiqueta={t.alerta.magnitud}>
          <input
            type="number"
            step="0.1"
            min="0"
            max="10"
            value={magnitud}
            onChange={(e) => setMagnitud(e.target.value)}
            className={ENTRADA}
            required={tipo === 'sismo'}
          />
        </CampoFormulario>
        <CampoFormulario etiqueta={t.alerta.profundidad}>
          <input
            type="number"
            step="0.1"
            min="0"
            value={profundidad}
            onChange={(e) => setProfundidad(e.target.value)}
            className={ENTRADA}
          />
        </CampoFormulario>
      </div>
      <fieldset>
        <legend className="text-sm font-medium">{t.alerta.ubicacion}</legend>
        <SelectorUbicacion
          ciudad={ciudad}
          valor={ubicacion}
          alCambiar={cambiarUbicacion}
          ayuda={t.alerta.ubicacionAyuda}
        />
      </fieldset>
      <Boton variante="principal" type="submit">
        {t.alerta.revisar}
      </Boton>
    </form>
  )
}
