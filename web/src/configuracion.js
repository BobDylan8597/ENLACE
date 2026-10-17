// Valores de presentación de la web (los del motor viven en servidor/config/enlace.toml).

// Prioridad (0–100) a partir de la cual un reporte se muestra como urgente o como importante
export const PRIORIDAD_URGENTE = 60
export const PRIORIDAD_IMPORTANTE = 40

// Zoom inicial del mapa y máximo del mapa base local (las teselas llegan hasta 15)
export const ZOOM_INICIAL = 13
export const ZOOM_MAXIMO = 18
export const ZOOM_MAXIMO_DATOS = 15

// Cada cuánto se refresca el "hace X min" de la bandeja
export const REFRESCO_RELOJ_MS = 30000
