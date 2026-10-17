import L from 'leaflet'
import { leafletLayer } from 'protomaps-leaflet'
import { useEffect, useRef } from 'react'

import { ZOOM_INICIAL, ZOOM_MAXIMO, ZOOM_MAXIMO_DATOS } from '../configuracion.js'

// Mapa con el mapa base local de la ciudad (.pmtiles servido por el propio servidor, sin
// internet). Entrega la instancia de Leaflet en `alListo` para que cada pantalla agregue capas.
export default function MapaBase({ ciudad, alListo, className = 'h-full w-full' }) {
  const contenedor = useRef(null)
  const ultimoAlListo = useRef(alListo)
  ultimoAlListo.current = alListo

  useEffect(() => {
    const mapa = L.map(contenedor.current, { maxZoom: ZOOM_MAXIMO }).setView(
      [ciudad.centro.lat, ciudad.centro.lon],
      ZOOM_INICIAL,
    )
    if (ciudad.mapa_disponible) {
      leafletLayer({
        url: '/mapa/base.pmtiles',
        flavor: 'light',
        lang: ciudad.idioma,
        maxDataZoom: ZOOM_MAXIMO_DATOS,
        attribution: ciudad.atribucion_mapa,
      }).addTo(mapa)
    }
    ultimoAlListo.current?.(mapa)
    return () => {
      ultimoAlListo.current?.(null)
      mapa.remove()
    }
  }, [ciudad])

  return <div ref={contenedor} className={className} />
}
