// Etiqueta + control de formulario, con el estilo común de las entradas
export const ENTRADA =
  'mt-1.5 block min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 font-normal shadow-xs transition focus:border-marca focus:ring-4 focus:ring-marca/20 focus:outline-none'

export default function CampoFormulario({ etiqueta, children }) {
  return (
    <label className="block text-sm font-medium text-slate-700">
      {etiqueta}
      {children}
    </label>
  )
}
