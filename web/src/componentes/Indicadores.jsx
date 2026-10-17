import { api } from '../api/cliente.js'
import { useDatos } from '../api/useDatos.js'
import { t } from '../textos/index.js'
import { entero } from '../util/formato.js'
import Icono from './Icono.jsx'

// Los cuatro números que el coordinador mira primero. Se actualizan solos por WebSocket.
export default function Indicadores({ evento }) {
  const celdas = useDatos(api.celdas, [evento?.id], ['celda_actualizada', 'checkin_recibido'])
  const pedidos = useDatos(() => api.reportes({ tipo: 'ayuda', estado: 'sin_asignar' }), [evento?.id], [
    'reporte_recibido',
    'asignacion_cambiada',
  ])
  const equipos = useDatos(api.equipos, [], ['asignacion_cambiada'])
  if (!evento) return null

  const afectadas = (celdas.datos ?? []).reduce((total, c) => total + c.impacto_estimado, 0)
  const criticos = (celdas.datos ?? []).reduce((total, c) => total + (c.checkins?.criticos ?? 0), 0)
  const disponibles = (equipos.datos ?? []).filter((e) => e.estado === 'disponible').length
  const tarjetas = [
    { icono: 'persona', color: 'text-noche bg-slate-100', valor: entero(afectadas), texto: t.indicadores.afectadas },
    { icono: 'alerta', color: 'text-actuar bg-actuar-suave', valor: entero(criticos), texto: t.indicadores.criticos },
    { icono: 'lista', color: 'text-explorar bg-explorar-suave', valor: pedidos.datos?.length ?? '—', texto: t.indicadores.pedidos },
    {
      icono: 'equipo',
      color: 'text-marca-oscuro bg-marca-suave',
      valor: equipos.datos ? `${disponibles}/${equipos.datos.length}` : '—',
      texto: t.indicadores.equipos,
    },
  ]
  return (
    <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
      {tarjetas.map((c) => (
        <div key={c.texto} className="tarjeta flex items-center gap-3 p-4">
          <span className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${c.color}`}>
            <Icono nombre={c.icono} className="size-6" />
          </span>
          <div className="min-w-0">
            <p className="text-2xl leading-tight font-bold text-noche tabular-nums">{c.valor}</p>
            <p className="text-sm leading-tight text-slate-600">{c.texto}</p>
          </div>
        </div>
      ))}
    </div>
  )
}
