import { t } from '../textos/index.js'

export function tiempoRelativo(fechaIso, ahora = Date.now()) {
  const minutos = Math.max(0, Math.round((ahora - new Date(fechaIso).getTime()) / 60000))
  if (minutos < 1) return t.comun.ahora
  if (minutos < 60) return t.comun.hace(t.comun.minutos(minutos))
  return t.comun.hace(t.comun.horas(Math.round(minutos / 60)))
}

export function porcentaje(valor) {
  return `${Math.round((valor ?? 0) * 100)} %`
}

export function distancia(metros) {
  return metros >= 1000
    ? t.comun.km((metros / 1000).toFixed(1).replace('.', ','))
    : t.comun.m(Math.round(metros))
}

export function entero(valor) {
  return Math.round(valor ?? 0).toLocaleString('es')
}

// "2 atrapados · 1 herido · necesita rescate, médico"
export function resumenReporte(reporte) {
  const partes = []
  if (reporte.atrapados) partes.push(t.reportes.atrapados(reporte.atrapados))
  if (reporte.heridos) partes.push(t.reportes.heridos(reporte.heridos))
  if (reporte.vulnerables) partes.push(t.reportes.vulnerables(reporte.vulnerables))
  if (!partes.length && reporte.personas) partes.push(t.reportes.personas(reporte.personas))
  if (reporte.nivel_dano) partes.push(t.nivelDano[reporte.nivel_dano])
  const necesidades = (reporte.necesidades ?? []).map((n) => t.necesidad[n] ?? n)
  if (necesidades.length) partes.push(necesidades.join(', '))
  return partes.join(' · ') || t.tipoReporte[reporte.tipo]
}
