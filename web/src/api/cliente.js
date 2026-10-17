// Cliente de la API de ENLACE. La web se sirve desde el mismo servidor, así que las rutas son
// relativas; en desarrollo, Vite las reenvía (ver vite.config.js).

const CLAVE_SESION = 'enlace.claveAdmin'
const NOMBRE_SESION = 'enlace.operador'

export function leerClave() {
  try {
    return sessionStorage.getItem(CLAVE_SESION)
  } catch {
    return null
  }
}

export function guardarClave(clave) {
  try {
    sessionStorage.setItem(CLAVE_SESION, clave)
  } catch {
    // Sin almacenamiento (ventana privada): la clave dura lo que la pestaña en memoria
  }
  claveEnMemoria = clave
}

export function borrarClave() {
  try {
    sessionStorage.removeItem(CLAVE_SESION)
  } catch {
    // nada que borrar
  }
  claveEnMemoria = null
}

let claveEnMemoria = leerClave()

// Nombre de quien opera la consola: queda registrado en cada decisión (decidido_por)
export function leerOperador() {
  try {
    return sessionStorage.getItem(NOMBRE_SESION) ?? operadorEnMemoria
  } catch {
    return operadorEnMemoria
  }
}

export function guardarOperador(nombre) {
  operadorEnMemoria = nombre
  try {
    sessionStorage.setItem(NOMBRE_SESION, nombre)
  } catch {
    // solo en memoria
  }
}

let operadorEnMemoria = null

export class ErrorApi extends Error {
  constructor(estado, detalle) {
    super(typeof detalle === 'string' ? detalle : JSON.stringify(detalle))
    this.estado = estado
    this.detalle = detalle
  }
}

async function pedir(metodo, ruta, cuerpo) {
  let respuesta
  try {
    respuesta = await fetch(ruta, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        ...(claveEnMemoria ? { 'X-Clave-Admin': claveEnMemoria } : {}),
      },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch (error) {
    throw new ErrorApi(0, error.message)
  }
  const texto = await respuesta.text()
  const datos = texto ? JSON.parse(texto) : null
  if (!respuesta.ok) throw new ErrorApi(respuesta.status, datos?.detail ?? respuesta.statusText)
  return datos
}

function consulta(parametros) {
  const limpios = Object.entries(parametros).filter(([, v]) => v !== undefined && v !== null && v !== '')
  return limpios.length ? `?${new URLSearchParams(limpios)}` : ''
}

export const api = {
  salud: () => pedir('GET', '/salud'),
  ciudad: () => pedir('GET', '/ciudad'),
  eventoActivo: () => pedir('GET', '/eventos/activo'),
  emitir: (datos) => pedir('POST', '/eventos', datos),
  cerrarEvento: (id) => pedir('POST', `/eventos/${id}/cerrar`),
  propuestas: () => pedir('GET', '/propuestas'),
  descartarPropuesta: (id) => pedir('POST', `/propuestas/${id}/descartar`),
  celdas: () => pedir('GET', '/celdas'),
  celda: (idH3) => pedir('GET', `/celdas/${idH3}`),
  reportes: (filtros = {}) => pedir('GET', `/reportes${consulta(filtros)}`),
  reporte: (id) => pedir('GET', `/reportes/${id}`),
  enviarReporte: (reporte) => pedir('POST', '/reportes', reporte),
  recomendaciones: () => pedir('GET', '/recomendaciones'),
  decidir: (id, decision) => pedir('POST', `/asignaciones/${id}/decision`, decision),
  marcarAtendida: (id) => pedir('POST', `/asignaciones/${id}/atendida`),
  asignaciones: (activas = true) => pedir('GET', `/asignaciones${consulta({ activas })}`),
  equipos: () => pedir('GET', '/equipos'),
  registrarEquipo: (equipo) => pedir('POST', '/equipos', equipo),
  bloqueos: () => pedir('GET', '/bloqueos'),
  decidirBloqueo: (decision) => pedir('POST', '/bloqueos', decision),
  // Puntos de quienes eligieron compartir su ubicación exacta durante la emergencia
  ubicaciones: () => pedir('GET', '/ubicaciones'),
  // Para comprobar la clave sin efectos: una ruta de administrador de solo lectura
  comprobarClave: () => pedir('GET', '/equipos'),
}

export function urlTiempoReal() {
  const protocolo = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocolo}://${location.host}/ws/admin?clave=${encodeURIComponent(claveEnMemoria ?? '')}`
}
