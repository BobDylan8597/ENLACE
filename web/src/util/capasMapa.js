import { cellToBoundary, cellToLatLng } from 'h3-js'
import L from 'leaflet'

import { t } from '../textos/index.js'
import { ESTILOS, estiloBloqueo, estiloCategoria } from './colores.js'
import { porcentaje, resumenReporte } from './formato.js'

// Opacidad del relleno de cada zona: lo urgente se ve más
const OPACIDAD = { actuar: 0.45, explorar: 0.25, estable: 0.2, explorar_despues: 0.1 }

// Leaflet inserta los textos emergentes como HTML: todo texto variable se escapa
export function escapar(texto) {
  return String(texto ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  )
}

export function capaCeldas(celdas, alElegir, seleccionada = null) {
  return L.layerGroup(
    celdas.map((celda) => {
      const estilo = estiloCategoria(celda.categoria)
      const elegida = celda.id_h3 === seleccionada
      return L.polygon(cellToBoundary(celda.id_h3), {
        color: elegida ? '#111827' : estilo.color,
        weight: elegida ? 4 : 1,
        fillColor: estilo.color,
        fillOpacity: OPACIDAD[celda.categoria] ?? 0.1,
      })
        .bindTooltip(
          `<strong>${escapar(celda.nombre_zona)}</strong><br>${escapar(t.categoria[celda.categoria])} · ` +
            `${escapar(t.detalle.prioridad)} ${Math.round(celda.prioridad)} · ` +
            `${escapar(t.celda.certeza)} ${porcentaje(celda.certeza)}`,
          { sticky: true },
        )
        .on('click', () => alElegir(celda.id_h3))
    }),
  )
}

// Check-ins de la celda (cada persona una vez, sea cual sea su estado)
export function totalCheckins(c) {
  return c.sin_respuesta + c.bien + c.ayuda
}

// Intensidad del relleno según la población crítica: crece rápido al principio y se satura
export function intensidadCritica(criticos, escala = 10) {
  return criticos <= 0 ? 0 : 0.15 + 0.55 * (1 - Math.exp(-criticos / escala))
}

export function capaCheckins(celdas) {
  return L.layerGroup(
    celdas
      .map((celda) => ({ celda, total: totalCheckins(celda.checkins) }))
      .filter(({ total }) => total > 0)
      .map(({ celda, total }) => {
        const piden = celda.checkins.ayuda / total
        const color = piden > 0.3 ? ESTILOS.actuar.color : ESTILOS.estable.color
        return L.circleMarker(cellToLatLng(celda.id_h3), {
          radius: 4 + Math.sqrt(total) * 2,
          color,
          weight: 2,
          fillOpacity: 0.6,
          interactive: true,
        }).bindTooltip(escapar(t.celda.totales(celda.checkins)))
      }),
  )
}

// Población crítica: vulnerables (adulto mayor, niños, embarazo, discapacidad…) que no
// respondieron o pidieron ayuda. Hexágono más intenso y el número escrito encima.
export function capaPoblacionCritica(celdas, alElegir) {
  const conCriticos = celdas.filter((c) => c.checkins.criticos > 0)
  return L.layerGroup([
    ...conCriticos.map((celda) =>
      L.polygon(cellToBoundary(celda.id_h3), {
        color: ESTILOS.actuar.color,
        weight: 1,
        fillColor: ESTILOS.actuar.color,
        fillOpacity: intensidadCritica(celda.checkins.criticos),
      })
        .bindTooltip(
          `<strong>${escapar(celda.nombre_zona)}</strong><br>${escapar(t.celda.poblacion(celda.checkins))}`,
          { sticky: true },
        )
        .on('click', () => alElegir(celda.id_h3)),
    ),
    ...conCriticos.map((celda) =>
      L.marker(cellToLatLng(celda.id_h3), {
        interactive: false,
        icon: L.divIcon({
          className: '',
          html: `<span class="marca-critica">⚠ ${escapar(celda.checkins.criticos)}</span>`,
          iconSize: [44, 22],
          iconAnchor: [22, 11],
        }),
      }),
    ),
  ])
}

// Símbolo de cada estado: el color nunca va solo (daltonismo)
const PERSONA = {
  sin_respuesta: { estilo: ESTILOS.sin_datos, simbolo: '?' },
  bien: { estilo: ESTILOS.estable, simbolo: '✓' },
  ayuda: { estilo: ESTILOS.actuar, simbolo: '!' },
}

export function estiloPersona(estado) {
  return PERSONA[estado] ?? PERSONA.sin_respuesta
}

// Personas que eligieron compartir su ubicación exacta durante la emergencia
export function capaPersonas(ubicaciones) {
  return L.layerGroup(
    ubicaciones.map((u) => {
      const { estilo, simbolo } = estiloPersona(u.estado)
      const vulnerable = u.vulnerables > 0 ? ' marca-persona--vulnerable' : ''
      return L.marker([u.lat, u.lon], {
        icon: L.divIcon({
          className: '',
          html: `<span class="marca-persona${vulnerable}" style="background:${estilo.color}">${simbolo}</span>`,
          iconSize: [18, 18],
          iconAnchor: [9, 9],
        }),
      }).bindTooltip(
        `<strong>${escapar(t.estadoPersona[u.estado])}</strong><br>${escapar(t.celda.hogar(u))}`,
      )
    }),
  )
}

export function capaPedidosDeAyuda(reportes, alElegir) {
  return L.layerGroup(
    reportes
      .filter((r) => r.ubicacion && r.estado !== 'atendido')
      .map((r) =>
        L.circleMarker([r.ubicacion.lat, r.ubicacion.lon], {
          radius: 8,
          color: '#ffffff',
          weight: 2,
          fillColor: ESTILOS.actuar.color,
          fillOpacity: 1,
        })
          .bindTooltip(`<strong>${escapar(t.tipoReporte.ayuda)}</strong><br>${escapar(resumenReporte(r))}`)
          .on('click', () => alElegir(r.id_mensaje)),
      ),
  )
}

// "Bomberos Estación Central" → "BC", "Cruz Roja Unidad 2" → "C2": distingue equipos parecidos
export function iniciales(nombre) {
  const palabras = nombre.trim().split(/\s+/)
  const ultima = palabras.at(-1) ?? ''
  const final = /^\d+$/.test(ultima) ? ultima : palabras.length > 1 ? ultima[0] : ''
  return `${palabras[0]?.[0] ?? ''}${final}`.toUpperCase()
}

export function capaEquipos(equipos) {
  return L.layerGroup(
    equipos.map((e) =>
      L.marker([e.lat, e.lon], {
        icon: L.divIcon({
          className: '',
          html: `<span class="marca-equipo marca-equipo--${escapar(e.estado)}">${escapar(iniciales(e.nombre))}</span>`,
          iconSize: [30, 30],
          iconAnchor: [15, 15],
        }),
      }).bindTooltip(
        `<strong>${escapar(e.nombre)}</strong><br>${escapar(t.estadoEquipo[e.estado])} · ` +
          escapar(e.capacidades.map((c) => t.capacidad[c]).join(', ')),
      ),
    ),
  )
}

export function capaBloqueos(bloqueos) {
  return L.layerGroup(
    bloqueos.map((b) =>
      L.marker([b.lat, b.lon], {
        icon: L.divIcon({
          className: '',
          html: `<span class="marca-bloqueo" style="background:${estiloBloqueo(b.estado).color}">✕</span>`,
          iconSize: [24, 24],
          iconAnchor: [12, 12],
        }),
      }).bindTooltip(
        `<strong>${escapar(t.tipoBloqueo[b.tipo])}</strong><br>${escapar(t.estadoBloqueo[b.estado])} · ` +
          `${b.confirmaciones} ${b.confirmaciones === 1 ? 'reporte' : 'reportes'}`,
      ),
    ),
  )
}

export function capaRutas(asignaciones) {
  return L.layerGroup(
    asignaciones
      .filter((a) => a.ruta)
      .map((a) =>
        L.geoJSON(a.ruta, {
          style: { color: ESTILOS.accion.color, weight: 5, opacity: 0.85 },
        }).bindTooltip(
          `<strong>${escapar(a.nombre_equipo)}</strong><br>${escapar(t.detalle.llegaEn(a.tiempo_estimado_min))}`,
          { sticky: true },
        ),
      ),
  )
}
