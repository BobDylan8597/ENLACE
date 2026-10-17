import { useCallback, useState } from 'react'

import { api } from '../api/cliente.js'
import { t } from '../textos/index.js'
import { ESTILOS } from '../util/colores.js'
import Boton from './Boton.jsx'
import CampoFormulario, { ENTRADA } from './CampoFormulario.jsx'
import SelectorUbicacion from './SelectorUbicacion.jsx'

const CAPACIDADES = ['rescate', 'medico', 'agua', 'reconocimiento', 'maquinaria']

export default function FormularioEquipo({ ciudad, alRegistrar }) {
  const [nombre, setNombre] = useState('')
  const [organismo, setOrganismo] = useState('')
  const [capacidades, setCapacidades] = useState([])
  const [ubicacion, setUbicacion] = useState(null)
  const [error, setError] = useState(null)
  const cambiarUbicacion = useCallback(setUbicacion, [])

  function alternar(capacidad) {
    setCapacidades((actuales) =>
      actuales.includes(capacidad) ? actuales.filter((c) => c !== capacidad) : [...actuales, capacidad],
    )
  }

  async function registrar(evento) {
    evento.preventDefault()
    setError(null)
    try {
      await api.registrarEquipo({ nombre, organismo, capacidades, lat: ubicacion.lat, lon: ubicacion.lon })
      setNombre('')
      setOrganismo('')
      setCapacidades([])
      setUbicacion(null)
      alRegistrar()
    } catch (falla) {
      setError(falla.message)
    }
  }

  return (
    <form onSubmit={registrar} className="space-y-3 rounded-xl bg-white p-5 shadow-sm">
      <h2 className="font-semibold">{t.equipos.nuevo}</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <CampoFormulario etiqueta={t.equipos.nombre}>
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} className={ENTRADA} required />
        </CampoFormulario>
        <CampoFormulario etiqueta={t.equipos.organismo}>
          <input
            value={organismo}
            onChange={(e) => setOrganismo(e.target.value)}
            className={ENTRADA}
            required
          />
        </CampoFormulario>
      </div>
      <fieldset>
        <legend className="text-sm font-medium">{t.equipos.capacidades}</legend>
        <div className="flex flex-wrap gap-x-4">
          {CAPACIDADES.map((c) => (
            <label key={c} className="flex min-h-11 items-center gap-2">
              <input
                type="checkbox"
                className="size-5"
                checked={capacidades.includes(c)}
                onChange={() => alternar(c)}
              />
              {t.capacidad[c]}
            </label>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend className="text-sm font-medium">{t.equipos.ubicacion}</legend>
        <SelectorUbicacion
          ciudad={ciudad}
          valor={ubicacion}
          alCambiar={cambiarUbicacion}
          ayuda={t.equipos.ubicacionAyuda}
          color={ESTILOS.accion.color}
        />
      </fieldset>
      {error && (
        <p className="text-sm text-actuar" role="alert">
          {error}
        </p>
      )}
      <Boton variante="principal" type="submit" disabled={!capacidades.length || !ubicacion}>
        {t.equipos.registrar}
      </Boton>
    </form>
  )
}
