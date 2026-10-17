import EventoActivo from '../componentes/EventoActivo.jsx'
import FormularioAlerta from '../componentes/FormularioAlerta.jsx'
import ListaPropuestas from '../componentes/ListaPropuestas.jsx'
import EncabezadoPagina from '../componentes/EncabezadoPagina.jsx'
import { t } from '../textos/index.js'

// A6: emitir alerta (siempre con confirmación humana) y cerrar el evento
export default function EmitirAlerta({ ciudad, evento, recargarEvento }) {
  return (
    <div className="mx-auto h-full max-w-4xl space-y-5 overflow-y-auto p-5 lg:p-8">
      <EncabezadoPagina titulo={t.alerta.titulo} detalle={t.alerta.detalle} />
      {evento ? (
        <EventoActivo evento={evento} alCerrar={recargarEvento} />
      ) : (
        <FormularioAlerta ciudad={ciudad} alEmitir={recargarEvento} />
      )}
      <ListaPropuestas hayEvento={Boolean(evento)} alAprobar={recargarEvento} />
    </div>
  )
}
