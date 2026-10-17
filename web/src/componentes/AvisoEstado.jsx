import { t } from '../textos/index.js'
import Boton from './Boton.jsx'
import Icono from './Icono.jsx'

// Estado de carga, error o vacío de una sección
export default function AvisoEstado({ cargando, error, vacio, alReintentar }) {
  if (cargando)
    return (
      <p className="flex items-center gap-3 p-6 text-slate-500">
        <span className="size-4 animate-spin rounded-full border-2 border-marca border-t-transparent" />
        {t.comun.cargando}
      </p>
    )
  if (error)
    return (
      <div className="m-4 rounded-2xl border border-actuar/30 bg-actuar-suave p-4 text-actuar" role="alert">
        <p className="font-medium">{t.comun.error}</p>
        <p className="text-sm">{error.message}</p>
        {alReintentar && (
          <Boton className="mt-2" onClick={alReintentar}>
            {t.comun.reintentar}
          </Boton>
        )}
      </div>
    )
  if (vacio)
    return (
      <div className="flex flex-col items-center gap-3 px-6 py-12 text-center text-slate-500">
        <span className="flex size-12 items-center justify-center rounded-2xl bg-marca-suave text-marca-oscuro">
          <Icono nombre="lista" className="size-6" />
        </span>
        <p className="max-w-sm">{vacio}</p>
      </div>
    )
  return null
}
