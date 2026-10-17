import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import AvisoEstado from '../componentes/AvisoEstado.jsx'
import FormularioEquipo from '../componentes/FormularioEquipo.jsx'
import Icono from '../componentes/Icono.jsx'
import { t } from '../textos/index.js'

const ESTILO_ESTADO = {
  disponible: 'text-estable bg-estable-suave',
  en_ruta: 'text-accion bg-accion-suave',
  ocupado: 'text-explorar bg-explorar-suave',
}
const ICONO_ESTADO = { disponible: 'bien', en_ruta: 'ruta', ocupado: 'equipo' }

// A12: equipos de respuesta y su estado
export default function Equipos({ ciudad }) {
  const equipos = useDatos(api.equipos, [], ['asignacion_cambiada'])

  return (
    <div className="mx-auto h-full max-w-5xl space-y-6 overflow-y-auto p-5">
      <h1 className="text-xl font-bold">{t.equipos.titulo}</h1>
      {equipos.datos?.length ? (
        <div className="overflow-x-auto rounded-xl bg-white shadow-sm">
          <table className="w-full text-left">
            <thead className="border-b border-gray-200 text-sm text-gray-600">
              <tr>
                <th className="px-4 py-2">{t.equipos.nombre}</th>
                <th className="px-4 py-2">{t.equipos.organismo}</th>
                <th className="px-4 py-2">{t.equipos.capacidades}</th>
                <th className="px-4 py-2">{t.equipos.estado}</th>
              </tr>
            </thead>
            <tbody>
              {equipos.datos.map((e) => (
                <tr key={e.id} className="border-b border-gray-100 last:border-0">
                  <td className="px-4 py-3 font-medium">{e.nombre}</td>
                  <td className="px-4 py-3">{e.organismo}</td>
                  <td className="px-4 py-3">{e.capacidades.map((c) => t.capacidad[c]).join(', ')}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-sm font-medium ${ESTILO_ESTADO[e.estado]}`}
                    >
                      <Icono nombre={ICONO_ESTADO[e.estado]} className="size-4" />
                      {t.estadoEquipo[e.estado]}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <AvisoEstado
          cargando={equipos.cargando}
          error={equipos.error}
          vacio={t.equipos.vacio}
          alReintentar={equipos.recargar}
        />
      )}
      <FormularioEquipo ciudad={ciudad} alRegistrar={equipos.recargar} />
    </div>
  )
}
