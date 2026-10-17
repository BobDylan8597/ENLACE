import { leerOperador } from '../api/cliente.js'
import logoBlanco from '../recursos/logo_enlace_blanco.png'
import { t } from '../textos/index.js'
import Icono from './Icono.jsx'

// [ruta, ícono, clave del texto], agrupadas por lo que hace el operador
const GRUPOS = [
  [
    'operacion',
    [
      ['reportes', 'lista', 'reportes'],
      ['mapa', 'mapa', 'tablero'],
      ['asignaciones', 'ruta', 'asignaciones'],
      ['bloqueos', 'bloqueo', 'bloqueos'],
    ],
  ],
  [
    'coordinacion',
    [
      ['alerta', 'alerta', 'alerta'],
      ['equipos', 'equipo', 'equipos'],
      ['manual', 'mas', 'manual'],
      ['paquete', 'lista', 'paquete'],
    ],
  ],
]

// Navegación del centro de coordinación: azul noche con el logo. En pantallas angostas pasa
// arriba como una fila que se desplaza.
export default function BarraLateral({ seccion, ciudad, alSalir }) {
  const operador = leerOperador()
  return (
    <aside className="flex shrink-0 flex-col bg-noche text-white lg:w-64">
      <div className="flex items-center gap-3 px-5 py-4 lg:flex-col lg:items-start lg:gap-1 lg:py-6">
        <img src={logoBlanco} alt={t.producto} className="h-8 w-auto lg:h-10" />
        <p className="text-xs font-medium tracking-wide text-white/60">
          {t.subtitulo} · {ciudad.nombre}
        </p>
      </div>
      <nav
        className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-1 lg:flex-col lg:gap-6 lg:overflow-visible lg:pb-0"
        aria-label="Secciones"
      >
        {GRUPOS.map(([grupo, secciones]) => (
          <div key={grupo} className="flex gap-1 lg:flex-col">
            <p className="hidden px-3 pb-1 text-[11px] font-semibold tracking-wider text-white/40 uppercase lg:block">
              {t.navegacion.grupos[grupo]}
            </p>
            {secciones.map(([ruta, icono, texto]) => {
              const activa = seccion === ruta
              return (
                <a
                  key={ruta}
                  href={`#/${ruta}`}
                  aria-current={activa ? 'page' : undefined}
                  className={`relative inline-flex min-h-11 shrink-0 items-center gap-3 rounded-xl px-3 text-sm font-medium whitespace-nowrap transition ${
                    activa ? 'bg-white/10 text-white' : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  {activa && (
                    <span className="absolute inset-y-2 left-0 hidden w-1 rounded-full bg-marca lg:block" aria-hidden="true" />
                  )}
                  <Icono nombre={icono} className={`size-5 ${activa ? 'text-marca' : ''}`} />
                  {t.navegacion[texto]}
                </a>
              )
            })}
          </div>
        ))}
      </nav>
      <div className="hidden border-t border-white/10 px-5 py-4 lg:block">
        <p className="text-xs text-white/50">{t.navegacion.operador}</p>
        <p className="truncate text-sm font-semibold">{operador || '—'}</p>
        <button type="button" onClick={alSalir} className="mt-2 min-h-11 text-sm text-white/70 hover:text-white hover:underline">
          {t.navegacion.salir}
        </button>
      </div>
    </aside>
  )
}
