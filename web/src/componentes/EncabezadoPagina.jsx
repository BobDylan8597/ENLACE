// Título de cada página: qué es y, debajo, para qué sirve. Acciones a la derecha.
export default function EncabezadoPagina({ titulo, detalle, children }) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="mr-auto min-w-0">
        <h1 className="text-2xl font-bold tracking-tight text-noche">{titulo}</h1>
        {detalle && <p className="mt-1 max-w-3xl text-slate-600">{detalle}</p>}
      </div>
      {children}
    </div>
  )
}
