import es from './es.js'

// Idiomas disponibles. Para agregar portugués: importar pt.js y sumarlo aquí.
const IDIOMAS = { es }
let actual = es

export function usarIdioma(codigo) {
  actual = IDIOMAS[codigo] ?? es
}

// `t.reportes.titulo`, `t.reportes.atrapados(2)`…: siempre en el idioma activo
export const t = new Proxy({}, { get: (_, clave) => actual[clave] })
