import { t } from '../textos/index.js'
import { estiloCategoria } from '../util/colores.js'
import Icono from './Icono.jsx'

// Categoría de una zona: color, ícono y texto juntos
export default function Insignia({ categoria, texto }) {
  const estilo = estiloCategoria(categoria)
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-sm font-semibold ring-1 ring-current/15 ${estilo.clase}`}>
      <Icono nombre={estilo.icono} className="size-4" />
      {texto ?? t.categoria[categoria] ?? t.categoria.sin_datos}
    </span>
  )
}
