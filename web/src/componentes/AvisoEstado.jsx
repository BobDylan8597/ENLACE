import { t } from '../textos/index.js'
import Boton from './Boton.jsx'

// Estado de carga, error o vacío de una sección
export default function AvisoEstado({ cargando, error, vacio, alReintentar }) {
  if (cargando) return <p className="p-6 text-gray-500">{t.comun.cargando}</p>
  if (error)
    return (
      <div className="m-4 rounded-lg border border-actuar bg-actuar-suave p-4 text-actuar" role="alert">
        <p className="font-medium">{t.comun.error}</p>
        <p className="text-sm">{error.message}</p>
        {alReintentar && (
          <Boton className="mt-2" onClick={alReintentar}>
            {t.comun.reintentar}
          </Boton>
        )}
      </div>
    )
  if (vacio) return <p className="p-6 text-gray-500">{vacio}</p>
  return null
}
