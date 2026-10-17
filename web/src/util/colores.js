import { PRIORIDAD_IMPORTANTE, PRIORIDAD_URGENTE } from '../configuracion.js'

// Significado fijo de cada color (CLAUDE.md, sección 16). El ícono acompaña siempre al color.
export const ESTILOS = {
  actuar: { color: '#c62828', suave: '#fdecea', clase: 'text-actuar bg-actuar-suave', icono: 'alerta' },
  explorar: { color: '#b45309', suave: '#fef3c7', clase: 'text-explorar bg-explorar-suave', icono: 'explorar' },
  estable: { color: '#2e7d32', suave: '#e8f5e9', clase: 'text-estable bg-estable-suave', icono: 'bien' },
  sin_datos: { color: '#6b7280', suave: '#f3f4f6', clase: 'text-sin-datos bg-sin-datos-suave', icono: 'pregunta' },
  accion: { color: '#1d4ed8', suave: '#dbeafe', clase: 'text-accion bg-accion-suave', icono: 'equipo' },
}

// "Explorar después" (poco impacto y poca información) se ve como "sin datos"
export function estiloCategoria(categoria) {
  return ESTILOS[categoria] ?? ESTILOS.sin_datos
}

export function estiloPrioridad(prioridad) {
  if (prioridad >= PRIORIDAD_URGENTE) return ESTILOS.actuar
  if (prioridad >= PRIORIDAD_IMPORTANTE) return ESTILOS.explorar
  return ESTILOS.sin_datos
}

export function estiloBloqueo(estado) {
  if (estado === 'confirmado') return ESTILOS.actuar
  if (estado === 'descartado') return ESTILOS.estable
  return ESTILOS.explorar
}
