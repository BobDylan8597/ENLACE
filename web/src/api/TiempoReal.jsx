import { createContext, useContext, useEffect, useRef, useState } from 'react'

import { urlTiempoReal } from './cliente.js'

// Conexión WebSocket con el servidor (patrón Observer). Se reconecta sola con espera creciente.
const ESPERA_INICIAL_MS = 1000
const ESPERA_MAXIMA_MS = 15000

const Contexto = createContext({ estado: 'desconectado', suscribir: () => () => {} })

export function TiempoRealProveedor({ children }) {
  const [estado, setEstado] = useState('reconectando')
  const oyentes = useRef(new Set())

  useEffect(() => {
    let socket
    let espera = ESPERA_INICIAL_MS
    let temporizador
    let cerrado = false

    function conectar() {
      socket = new WebSocket(urlTiempoReal())
      socket.onopen = () => {
        espera = ESPERA_INICIAL_MS
        setEstado('conectado')
      }
      socket.onmessage = (evento) => {
        const mensaje = JSON.parse(evento.data)
        oyentes.current.forEach((oyente) => oyente(mensaje))
      }
      socket.onclose = () => {
        if (cerrado) return
        setEstado(espera > ESPERA_INICIAL_MS * 4 ? 'desconectado' : 'reconectando')
        temporizador = setTimeout(conectar, espera)
        espera = Math.min(espera * 2, ESPERA_MAXIMA_MS)
      }
    }

    conectar()
    return () => {
      cerrado = true
      clearTimeout(temporizador)
      socket?.close()
    }
  }, [])

  function suscribir(oyente) {
    oyentes.current.add(oyente)
    return () => oyentes.current.delete(oyente)
  }

  return <Contexto.Provider value={{ estado, suscribir }}>{children}</Contexto.Provider>
}

export function useEstadoConexion() {
  return useContext(Contexto).estado
}

// Llama a `alRecibir(mensaje)` con cada mensaje cuyo tipo esté en `tipos`
export function useMensajes(tipos, alRecibir) {
  const { suscribir } = useContext(Contexto)
  const ultimo = useRef(alRecibir)
  ultimo.current = alRecibir
  const clave = tipos.join(',')

  useEffect(
    () =>
      suscribir((mensaje) => {
        if (clave.split(',').includes(mensaje.tipo)) ultimo.current(mensaje)
      }),
    [suscribir, clave],
  )
}
