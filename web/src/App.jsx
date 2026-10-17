import { useEffect, useState } from 'react'

import { api, borrarClave, leerClave } from './api/cliente.js'
import { TiempoRealProveedor } from './api/TiempoReal.jsx'
import { useDatos } from './api/useDatos.js'
import AvisoEstado from './componentes/AvisoEstado.jsx'
import Encabezado from './componentes/Encabezado.jsx'
import Ingreso from './componentes/Ingreso.jsx'
import CentroReportes from './paginas/CentroReportes.jsx'
import EmitirAlerta from './paginas/EmitirAlerta.jsx'
import Equipos from './paginas/Equipos.jsx'
import ReporteManual from './paginas/ReporteManual.jsx'
import Tablero from './paginas/Tablero.jsx'
import { t, usarIdioma } from './textos/index.js'
import { useRutaHash } from './util/useRutaHash.js'

const PAGINAS = {
  reportes: ({ evento, parametro }) => <CentroReportes evento={evento} seleccionado={parametro} />,
  mapa: ({ ciudad, evento, parametro }) => (
    <Tablero ciudad={ciudad} evento={evento} parametro={parametro} />
  ),
  alerta: ({ ciudad, evento, recargarEvento }) => (
    <EmitirAlerta ciudad={ciudad} evento={evento} recargarEvento={recargarEvento} />
  ),
  equipos: ({ ciudad }) => <Equipos ciudad={ciudad} />,
  manual: ({ ciudad }) => <ReporteManual ciudad={ciudad} />,
}

function Consola({ ciudad, alSalir }) {
  const { seccion, parametro } = useRutaHash()
  const evento = useDatos(api.eventoActivo, [], ['evento_emitido', 'evento_cerrado'])
  const Pagina = PAGINAS[seccion] ?? PAGINAS.reportes
  return (
    <div className="flex h-full flex-col">
      <Encabezado seccion={seccion} evento={evento.datos} ciudad={ciudad} alSalir={alSalir} />
      <main className="min-h-0 flex-1">
        <Pagina
          ciudad={ciudad}
          evento={evento.datos}
          parametro={parametro}
          recargarEvento={evento.recargar}
        />
      </main>
    </div>
  )
}

export default function App() {
  const [ciudad, setCiudad] = useState(null)
  const [error, setError] = useState(null)
  const [conClave, setConClave] = useState(Boolean(leerClave()))

  useEffect(() => {
    api
      .ciudad()
      .then((datos) => {
        usarIdioma(datos.idioma)
        document.title = `${t.producto} · ${datos.nombre}`
        setCiudad(datos)
      })
      .catch(setError)
  }, [])

  if (!ciudad) return <AvisoEstado cargando={!error} error={error} />
  if (!conClave) return <Ingreso alIngresar={() => setConClave(true)} />

  function salir() {
    borrarClave()
    setConClave(false)
  }

  return (
    <TiempoRealProveedor>
      <Consola ciudad={ciudad} alSalir={salir} />
    </TiempoRealProveedor>
  )
}
