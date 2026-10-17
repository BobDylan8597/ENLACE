import { describe, expect, it } from 'vitest'

import { escapar, estiloPersona, iniciales, intensidadCritica, totalCheckins } from '../util/capasMapa.js'
import { estiloCategoria, estiloPrioridad, ESTILOS } from '../util/colores.js'
import { distancia, lineasInfraestructura, resumenReporte, tiempoRelativo } from '../util/formato.js'
import { nuevoId } from '../util/identificadores.js'
import { leerRuta } from '../util/useRutaHash.js'
import { PRIORIDAD_IMPORTANTE, PRIORIDAD_URGENTE } from '../configuracion.js'

describe('formato', () => {
  it('tiempo relativo en minutos y horas', () => {
    const ahora = Date.parse('2026-10-16T14:00:00Z')
    expect(tiempoRelativo('2026-10-16T14:00:20Z', ahora)).toBe('recién')
    expect(tiempoRelativo('2026-10-16T13:45:00Z', ahora)).toBe('hace 15 min')
    expect(tiempoRelativo('2026-10-16T11:00:00Z', ahora)).toBe('hace 3 h')
  })

  it('distancias legibles', () => {
    expect(distancia(420)).toBe('420 m')
    expect(distancia(3400)).toBe('3,4 km')
  })

  it('resumen de un pedido de ayuda', () => {
    const reporte = { tipo: 'ayuda', atrapados: 2, heridos: 1, vulnerables: 0, personas: 4, necesidades: ['rescate', 'medico'] }
    expect(resumenReporte(reporte)).toBe('2 atrapados · 1 herido · Rescate, Médico')
    expect(resumenReporte({ tipo: 'bien', necesidades: [] })).toBe('Está bien')
  })
})

describe('colores con significado fijo', () => {
  it('cada categoría tiene color e ícono; lo desconocido es "sin datos"', () => {
    expect(estiloCategoria('actuar')).toBe(ESTILOS.actuar)
    expect(estiloCategoria('explorar_despues')).toBe(ESTILOS.sin_datos)
    for (const estilo of Object.values(ESTILOS)) expect(estilo.icono).toBeTruthy()
  })

  it('prioridad: urgente en rojo, importante en ámbar', () => {
    expect(estiloPrioridad(PRIORIDAD_URGENTE)).toBe(ESTILOS.actuar)
    expect(estiloPrioridad(PRIORIDAD_IMPORTANTE)).toBe(ESTILOS.explorar)
    expect(estiloPrioridad(PRIORIDAD_IMPORTANTE - 1)).toBe(ESTILOS.sin_datos)
  })
})

describe('mapa', () => {
  it('escapa el texto que Leaflet inserta como HTML', () => {
    expect(escapar('<img src=x onerror=alert(1)>')).toBe('&lt;img src=x onerror=alert(1)&gt;')
  })

  it('iniciales que distinguen equipos parecidos', () => {
    expect(iniciales('Bomberos Estación Central')).toBe('BC')
    expect(iniciales('Bomberos Estación Sur')).toBe('BS')
    expect(iniciales('Cruz Roja Unidad 2')).toBe('C2')
    expect(iniciales('Cisterna')).toBe('C')
  })
})

describe('identificadores y navegación', () => {
  it('UUID v4 válidos y distintos sin crypto.randomUUID', () => {
    const a = nuevoId()
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(nuevoId()).not.toBe(a)
  })

  it('lee la sección y el parámetro del hash', () => {
    expect(leerRuta('')).toEqual({ seccion: 'reportes', parametro: null })
    expect(leerRuta('#/mapa/88283082bffffff')).toEqual({ seccion: 'mapa', parametro: '88283082bffffff' })
  })
})

describe('población y personas ubicadas', () => {
  it('cuenta cada check-in una vez (sin sumar los conteos del hogar)', () => {
    const c = { sin_respuesta: 2, bien: 3, ayuda: 1, personas: 14, vulnerables: 4, criticos: 2 }
    expect(totalCheckins(c)).toBe(6)
  })

  it('la intensidad crece con la población crítica y se satura', () => {
    expect(intensidadCritica(0)).toBe(0)
    expect(intensidadCritica(2)).toBeLessThan(intensidadCritica(10))
    expect(intensidadCritica(1000)).toBeCloseTo(0.7)
  })

  it('cada estado tiene color y símbolo', () => {
    expect(estiloPersona('ayuda').estilo).toBe(ESTILOS.actuar)
    expect(estiloPersona('bien').simbolo).toBe('✓')
    expect(estiloPersona('otro').estilo).toBe(ESTILOS.sin_datos)
  })
})

describe('infraestructura crítica', () => {
  it('con cantidad y nombres de OSM o con el formato sintético', () => {
    const osm = { hospital: { cantidad: 2, nombres: ['Hospital A', 'Hospital B'] }, escuela: { cantidad: 1, nombres: [] } }
    expect(lineasInfraestructura(osm)).toEqual(['2 hospitales: Hospital A, Hospital B', '1 escuela'])
    expect(lineasInfraestructura({ albergue: 1 })).toEqual(['1 albergue'])
    expect(lineasInfraestructura(null)).toEqual([])
  })
})
