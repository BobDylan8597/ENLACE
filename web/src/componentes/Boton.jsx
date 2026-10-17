// Botones de al menos 44 px de alto. "principal" es turquesa: la acción principal de la pantalla.
const VARIANTES = {
  principal: 'bg-marca-oscuro text-white shadow-sm hover:bg-noche disabled:bg-slate-300',
  secundario:
    'bg-white text-slate-800 border border-slate-300 shadow-xs hover:border-marca hover:text-marca-oscuro disabled:text-slate-400',
  peligro: 'bg-actuar text-white shadow-sm hover:bg-red-800 disabled:bg-red-300',
  texto: 'text-marca-oscuro hover:bg-marca-suave',
}

export default function Boton({ variante = 'secundario', className = '', children, ...props }) {
  return (
    <button
      type="button"
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 font-semibold transition focus-visible:ring-4 focus-visible:ring-marca/30 focus-visible:outline-none disabled:cursor-not-allowed ${VARIANTES[variante]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
