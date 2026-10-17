// Botones de al menos 44 px de alto. "principal" es azul: la acción principal de la pantalla.
const VARIANTES = {
  principal: 'bg-accion text-white hover:bg-blue-800 disabled:bg-blue-300',
  secundario: 'bg-white text-gray-900 border border-gray-300 hover:bg-gray-100 disabled:text-gray-400',
  peligro: 'bg-actuar text-white hover:bg-red-800 disabled:bg-red-300',
  texto: 'text-accion hover:bg-accion-suave',
}

export default function Boton({ variante = 'secundario', className = '', children, ...props }) {
  return (
    <button
      type="button"
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 font-medium transition-colors disabled:cursor-not-allowed ${VARIANTES[variante]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
