import { useEffect, useState } from 'react'

// Navegación por "#/seccion/parametro": no necesita configuración en el servidor
export function leerRuta(hash = location.hash) {
  const [seccion = 'reportes', ...resto] = hash.replace(/^#\/?/, '').split('/')
  return { seccion: seccion || 'reportes', parametro: resto.join('/') || null }
}

export function navegar(seccion, parametro = null) {
  location.hash = parametro ? `#/${seccion}/${parametro}` : `#/${seccion}`
}

export function useRutaHash() {
  const [ruta, setRuta] = useState(leerRuta)
  useEffect(() => {
    const alCambiar = () => setRuta(leerRuta())
    window.addEventListener('hashchange', alCambiar)
    return () => window.removeEventListener('hashchange', alCambiar)
  }, [])
  return ruta
}
