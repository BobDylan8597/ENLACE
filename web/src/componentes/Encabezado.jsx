import { t } from '../textos/index.js'
import { navegar } from '../util/useRutaHash.js'
import Icono from './Icono.jsx'
import IndicadorConexion from './IndicadorConexion.jsx'

// [ruta, ícono, clave del texto]
const SECCIONES = [
  ['reportes', 'lista', 'reportes'],
  ['mapa', 'mapa', 'tablero'],
  ['alerta', 'alerta', 'alerta'],
  ['asignaciones', 'ruta', 'asignaciones'],
  ['bloqueos', 'bloqueo', 'bloqueos'],
  ['equipos', 'equipo', 'equipos'],
  ['manual', 'mas', 'manual'],
  ['paquete', 'lista', 'paquete'],
]

export default function Encabezado({ seccion, evento, ciudad, alSalir }) {
  return (
    <header className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-gray-200 bg-white px-4 py-2">
      <div className="flex items-baseline gap-2">
        <span className="text-xl font-black tracking-wide text-accion">{t.producto}</span>
        <span className="text-sm text-gray-500">
          {t.subtitulo} · {ciudad.nombre}
        </span>
      </div>
      <nav className="flex flex-wrap gap-1" aria-label="Secciones">
        {SECCIONES.map(([ruta, icono, texto]) => (
          <a
            key={ruta}
            href={`#/${ruta}`}
            aria-current={seccion === ruta ? 'page' : undefined}
            className={`inline-flex min-h-11 items-center gap-1.5 rounded-lg px-3 font-medium ${
              seccion === ruta ? 'bg-accion-suave text-accion' : 'text-gray-700 hover:bg-gray-100'
            }`}
          >
            <Icono nombre={icono} className="size-4" />
            {t.navegacion[texto]}
          </a>
        ))}
      </nav>
      <div className="ml-auto flex items-center gap-3">
        {evento ? (
          <button
            type="button"
            onClick={() => navegar('alerta')}
            className="inline-flex items-center gap-1.5 rounded-full bg-actuar px-3 py-1 text-sm font-semibold text-white"
          >
            <Icono nombre="alerta" className="size-4" />
            {t.evento.activo(t.tipoEvento[evento.tipo], evento.magnitud)}
          </button>
        ) : (
          <span className="rounded-full bg-sin-datos-suave px-3 py-1 text-sm text-sin-datos">
            {t.evento.sinEvento}
          </span>
        )}
        <IndicadorConexion />
        <button
          type="button"
          onClick={alSalir}
          className="min-h-11 px-2 text-sm text-gray-600 hover:underline"
        >
          {t.navegacion.salir}
        </button>
      </div>
    </header>
  )
}
