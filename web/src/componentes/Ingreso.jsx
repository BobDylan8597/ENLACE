import { useState } from 'react'

import { api, borrarClave, guardarClave, guardarOperador, leerOperador } from '../api/cliente.js'
import logoBlanco from '../recursos/logo_enlace_blanco.png'
import logoTurquesa from '../recursos/logo_enlace_turquesa.png'
import { t } from '../textos/index.js'
import Boton from './Boton.jsx'
import { ENTRADA } from './CampoFormulario.jsx'
import Icono from './Icono.jsx'

// A1: ingreso al centro de coordinación. A la izquierda, la marca; a la derecha, el formulario.
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
    <main className="grid min-h-full lg:grid-cols-[1.1fr_1fr]">
      <section className="relative hidden overflow-hidden bg-noche p-12 text-white lg:flex lg:flex-col lg:justify-between">
        {/* Ondas del logo, de fondo */}
        <div className="pointer-events-none absolute -top-40 -left-40 size-[36rem] rounded-full border-[3rem] border-marca/10" />
        <div className="pointer-events-none absolute -top-24 -left-24 size-[26rem] rounded-full border-[2rem] border-marca/10" />
        <img src={logoBlanco} alt={t.producto} className="relative h-14 w-auto self-start" />
        <div className="relative space-y-6">
          <p className="max-w-md text-3xl leading-snug font-semibold">{t.ingreso.lema}</p>
          <ul className="space-y-3 text-white/80">
            {t.ingreso.puntos.map((punto, i) => (
              <li key={punto} className="flex items-start gap-3">
                <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg bg-marca/20 text-marca">
                  <Icono nombre={['senal', 'mapa', 'equipo'][i]} className="size-4" />
                </span>
                {punto}
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-sm text-white/50">{t.ingreso.pie}</p>
      </section>

      <section className="flex items-center justify-center p-6">
        <form onSubmit={entrar} className="w-full max-w-sm space-y-5">
          <img src={logoTurquesa} alt={t.producto} className="h-10 w-auto lg:hidden" />
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-noche">{t.ingreso.titulo}</h1>
            <p className="mt-1 text-slate-600">{t.ingreso.detalle}</p>
          </div>
          <label className="block text-sm font-medium text-slate-700">
            {t.ingreso.nombre}
            <input value={nombre} onChange={(e) => setNombre(e.target.value)} className={ENTRADA} autoFocus required />
            <span className="mt-1 block text-xs font-normal text-slate-500">{t.ingreso.nombreAyuda}</span>
          </label>
          <label className="block text-sm font-medium text-slate-700">
            {t.ingreso.clave}
            <input type="password" value={clave} onChange={(e) => setClave(e.target.value)} className={ENTRADA} required />
          </label>
          {error && (
            <p className="flex items-center gap-2 rounded-xl bg-actuar-suave px-3 py-2 text-sm font-medium text-actuar" role="alert">
              <Icono nombre="alerta" className="size-4" />
              {error}
            </p>
          )}
          <Boton variante="principal" type="submit" className="w-full" disabled={enviando}>
            {t.ingreso.entrar}
          </Boton>
        </form>
      </section>
    </main>
  )
}
