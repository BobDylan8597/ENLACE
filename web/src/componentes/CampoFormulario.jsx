// Etiqueta + control de formulario, con el estilo común de las entradas
export const ENTRADA =
  'mt-1 block min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3 font-normal'

export default function CampoFormulario({ etiqueta, children }) {
  return (
    <label className="block text-sm font-medium">
      {etiqueta}
      {children}
    </label>
  )
}
