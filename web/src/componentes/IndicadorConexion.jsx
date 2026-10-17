import { useEstadoConexion } from '../api/TiempoReal.jsx'
import { t } from '../textos/index.js'
import Icono from './Icono.jsx'

// Siempre visible (sección 16): el operador sabe si lo que ve está al día
export default function IndicadorConexion() {
  const estado = useEstadoConexion()
  const estilos = {
    conectado: 'text-estable bg-estable-suave',
    reconectando: 'text-explorar bg-explorar-suave',
    desconectado: 'text-actuar bg-actuar-suave',
  }
  return (
    <span
      className={`inline-flex min-h-9 items-center gap-1.5 rounded-full px-3 text-sm font-semibold ${estilos[estado]}`}
      role="status"
    >
      <Icono nombre={estado === 'conectado' ? 'senal' : 'sinSenal'} className="size-4" />
      {t.conexion[estado]}
    </span>
  )
}
