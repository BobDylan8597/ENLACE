import { t } from '../textos/index.js'
import { navegar } from '../util/useRutaHash.js'
import Icono from './Icono.jsx'
import IndicadorConexion from './IndicadorConexion.jsx'

// Estado general siempre visible: emergencia activa y conexión con el servidor (sección 16)
export default function BarraSuperior({ evento, alSalir }) {
  return (
    <header className="flex flex-wrap items-center gap-3 border-b border-slate-200 bg-white px-5 py-3">
      {evento ? (
        <button
          type="button"
          onClick={() => navegar('alerta')}
          className="inline-flex min-h-9 items-center gap-2 rounded-full bg-actuar px-4 text-sm font-semibold text-white shadow-sm ring-4 ring-actuar/15"
        >
          <span className="relative flex size-2.5">
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-white/70" />
            <span className="relative inline-flex size-2.5 rounded-full bg-white" />
          </span>
          {t.evento.activo(t.tipoEvento[evento.tipo], evento.magnitud)}
        </button>
      ) : (
        <span className="inline-flex min-h-9 items-center gap-2 rounded-full bg-slate-100 px-4 text-sm font-medium text-slate-600">
          <Icono nombre="bien" className="size-4" />
          {t.evento.sinEvento}
        </span>
      )}
      <div className="ml-auto flex items-center gap-3">
        <IndicadorConexion />
        <button
          type="button"
          onClick={alSalir}
          className="min-h-11 rounded-xl px-3 text-sm font-medium text-slate-600 hover:bg-slate-100 lg:hidden"
        >
          {t.navegacion.salir}
        </button>
      </div>
    </header>
  )
}
