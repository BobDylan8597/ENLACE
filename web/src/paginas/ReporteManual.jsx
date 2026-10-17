import { useCallback, useState } from 'react'

import { api } from '../api/cliente.js'
import Boton from '../componentes/Boton.jsx'
import CampoFormulario, { ENTRADA } from '../componentes/CampoFormulario.jsx'
import SelectorUbicacion from '../componentes/SelectorUbicacion.jsx'
import { t } from '../textos/index.js'
import { nuevoId } from '../util/identificadores.js'
import { navegar } from '../util/useRutaHash.js'

const ORIGENES = ['radio', 'brigada', 'app']
const NECESIDADES = ['rescate', 'medico', 'agua', 'refugio']
const NIVELES = ['ninguno', 'leve', 'moderado', 'grave', 'colapso']
const CONTEOS = ['personas', 'atrapados', 'heridos', 'vulnerables']
const VACIO = { personas: 0, atrapados: 0, heridos: 0, vulnerables: 0 }

// A9: el operador carga un reporte recibido por teléfono, radio o en persona
export default function ReporteManual({ ciudad }) {
  const [origen, setOrigen] = useState('radio')
  const [tipo, setTipo] = useState('ayuda')
  const [ubicacion, setUbicacion] = useState(null)
  const [conteos, setConteos] = useState(VACIO)
  const [necesidades, setNecesidades] = useState([])
  const [nivelDano, setNivelDano] = useState('moderado')
  const [nombre, setNombre] = useState('')
  const [telefono, setTelefono] = useState('')
  const [resultado, setResultado] = useState(null)
  const cambiarUbicacion = useCallback(setUbicacion, [])

  // Solo una brigada evalúa daños (el servidor lo exige)
  const tipos = origen === 'brigada' ? ['ayuda', 'evaluacion', 'bien'] : ['ayuda', 'bien']
  const tipoValido = tipos.includes(tipo) ? tipo : 'ayuda'

  function alternar(necesidad) {
    setNecesidades((actuales) =>
      actuales.includes(necesidad) ? actuales.filter((n) => n !== necesidad) : [...actuales, necesidad],
    )
  }

  async function enviar(evento) {
    evento.preventDefault()
    const id = nuevoId()
    const identidad =
      tipoValido === 'ayuda' && (nombre || telefono)
        ? { nombre: nombre || null, telefono: telefono || null }
        : undefined
    try {
      const respuesta = await api.enviarReporte({
        id_mensaje: id,
        id_anonimo: nuevoId(),
        origen,
        tipo: tipoValido,
        lat: ubicacion.lat,
        lon: ubicacion.lon,
        ...conteos,
        necesidades: tipoValido === 'ayuda' ? necesidades : [],
        ...(tipoValido === 'evaluacion' ? { nivel_dano: nivelDano } : {}),
        ...(identidad ? { identidad } : {}),
      })
      if (respuesta.aceptados.includes(id)) {
        setResultado({ tipo: 'ok', texto: t.manual.enviado, id })
        setConteos(VACIO)
        setNecesidades([])
        setNombre('')
        setTelefono('')
        setUbicacion(null)
      } else if (respuesta.repetidos.includes(id)) {
        setResultado({ tipo: 'aviso', texto: t.manual.repetido })
      } else {
        setResultado({ tipo: 'error', texto: respuesta.rechazados.map((r) => r.error).join('; ') })
      }
    } catch (falla) {
      setResultado({ tipo: 'error', texto: falla.message })
    }
  }

  return (
    <div className="mx-auto h-full max-w-4xl overflow-y-auto p-5">
      <form onSubmit={enviar} className="space-y-4 rounded-xl bg-white p-5 shadow-sm">
        <div>
          <h1 className="text-xl font-bold">{t.manual.titulo}</h1>
          <p className="text-gray-600">{t.manual.ayuda}</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <CampoFormulario etiqueta={t.manual.origen}>
            <select value={origen} onChange={(e) => setOrigen(e.target.value)} className={ENTRADA}>
              {ORIGENES.map((o) => (
                <option key={o} value={o}>
                  {t.origen[o]}
                </option>
              ))}
            </select>
          </CampoFormulario>
          <CampoFormulario etiqueta={t.manual.tipo}>
            <select value={tipoValido} onChange={(e) => setTipo(e.target.value)} className={ENTRADA}>
              {tipos.map((valor) => (
                <option key={valor} value={valor}>
                  {t.tipoReporte[valor]}
                </option>
              ))}
            </select>
          </CampoFormulario>
        </div>

        <fieldset>
          <legend className="text-sm font-medium">{t.manual.ubicacion}</legend>
          <SelectorUbicacion
            ciudad={ciudad}
            valor={ubicacion}
            alCambiar={cambiarUbicacion}
            ayuda={t.manual.ubicacionAyuda}
          />
        </fieldset>

        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {CONTEOS.map((campo) => (
            <CampoFormulario key={campo} etiqueta={t.manual[campo]}>
              <input
                type="number"
                min="0"
                value={conteos[campo]}
                onChange={(e) => setConteos({ ...conteos, [campo]: Math.max(0, Number(e.target.value)) })}
                className={ENTRADA}
              />
            </CampoFormulario>
          ))}
        </div>

        {tipoValido === 'ayuda' && (
          <>
            <fieldset>
              <legend className="text-sm font-medium">{t.manual.necesidades}</legend>
              <div className="flex flex-wrap gap-x-4">
                {NECESIDADES.map((n) => (
                  <label key={n} className="flex min-h-11 items-center gap-2">
                    <input
                      type="checkbox"
                      className="size-5"
                      checked={necesidades.includes(n)}
                      onChange={() => alternar(n)}
                    />
                    {t.necesidad[n]}
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="grid gap-3 sm:grid-cols-2">
              <CampoFormulario etiqueta={t.manual.nombre}>
                <input value={nombre} onChange={(e) => setNombre(e.target.value)} className={ENTRADA} />
              </CampoFormulario>
              <CampoFormulario etiqueta={t.manual.telefono}>
                <input
                  type="tel"
                  value={telefono}
                  onChange={(e) => setTelefono(e.target.value)}
                  className={ENTRADA}
                />
              </CampoFormulario>
            </div>
          </>
        )}

        {tipoValido === 'evaluacion' && (
          <CampoFormulario etiqueta={t.manual.nivelDano}>
            <select value={nivelDano} onChange={(e) => setNivelDano(e.target.value)} className={ENTRADA}>
              {NIVELES.map((n) => (
                <option key={n} value={n}>
                  {t.nivelDano[n]}
                </option>
              ))}
            </select>
          </CampoFormulario>
        )}

        {resultado && (
          <p
            role="status"
            className={`rounded-lg p-3 ${
              {
                ok: 'bg-estable-suave text-estable',
                aviso: 'bg-explorar-suave text-explorar',
                error: 'bg-actuar-suave text-actuar',
              }[resultado.tipo]
            }`}
          >
            {resultado.texto}
            {resultado.id && (
              <button
                type="button"
                className="ml-2 underline"
                onClick={() => navegar('reportes', resultado.id)}
              >
                {t.navegacion.reportes}
              </button>
            )}
          </p>
        )}

        <Boton variante="principal" type="submit" disabled={!ubicacion}>
          {t.manual.enviar}
        </Boton>
      </form>
    </div>
  )
}
