import { useState } from 'react'

import { api, borrarClave, guardarClave, guardarOperador, leerOperador } from '../api/cliente.js'
import { t } from '../textos/index.js'
import Boton from './Boton.jsx'

export default function Ingreso({ alIngresar }) {
  const [clave, setClave] = useState('')
  const [nombre, setNombre] = useState(leerOperador() ?? '')
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function entrar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setError(null)
    guardarClave(clave)
    guardarOperador(nombre.trim())
    try {
      await api.comprobarClave()
      alIngresar()
    } catch (falla) {
      borrarClave()
      setError(falla.estado === 401 ? t.ingreso.incorrecta : t.ingreso.sinServidor)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="flex min-h-full items-center justify-center p-4">
      <form onSubmit={entrar} className="w-full max-w-sm space-y-4 rounded-2xl bg-white p-6 shadow">
        <div>
          <p className="text-3xl font-black tracking-wide text-accion">{t.producto}</p>
          <h1 className="text-lg text-gray-700">{t.ingreso.titulo}</h1>
        </div>
        <label className="block">
          <span className="text-sm font-medium">{t.ingreso.nombre}</span>
          <input
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            className="mt-1 block min-h-11 w-full rounded-lg border border-gray-300 px-3"
            autoFocus
            required
          />
          <span className="text-xs text-gray-500">{t.ingreso.nombreAyuda}</span>
        </label>
        <label className="block">
          <span className="text-sm font-medium">{t.ingreso.clave}</span>
          <input
            type="password"
            value={clave}
            onChange={(e) => setClave(e.target.value)}
            className="mt-1 block min-h-11 w-full rounded-lg border border-gray-300 px-3"
            required
          />
        </label>
        {error && (
          <p className="text-sm text-actuar" role="alert">
            {error}
          </p>
        )}
        <Boton variante="principal" type="submit" className="w-full" disabled={enviando}>
          {t.ingreso.entrar}
        </Boton>
      </form>
    </main>
  )
}
