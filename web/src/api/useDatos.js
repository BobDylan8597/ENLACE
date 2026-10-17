import { useCallback, useEffect, useRef, useState } from 'react'

import { useMensajes } from './TiempoReal.jsx'

// Espera para agrupar varios avisos seguidos en una sola recarga
const AGRUPAR_MS = 300

/**
 * Carga datos de la API y los vuelve a cargar cuando llega por WebSocket alguno de
 * `recargarCon` (p. ej. ['reporte_recibido']). Así la pantalla está siempre al día.
 */
export function useDatos(cargar, dependencias = [], recargarCon = []) {
  const [estado, setEstado] = useState({ datos: null, error: null, cargando: true })
  const temporizador = useRef()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const cargarMemo = useCallback(cargar, dependencias)

  const recargar = useCallback(async () => {
    try {
      const datos = await cargarMemo()
      setEstado({ datos, error: null, cargando: false })
    } catch (error) {
      setEstado((anterior) => ({ ...anterior, error, cargando: false }))
    }
  }, [cargarMemo])

  useEffect(() => {
    setEstado((anterior) => ({ ...anterior, cargando: true }))
    recargar()
  }, [recargar])

  useMensajes(recargarCon, () => {
    clearTimeout(temporizador.current)
    temporizador.current = setTimeout(recargar, AGRUPAR_MS)
  })

  return { ...estado, recargar }
}
