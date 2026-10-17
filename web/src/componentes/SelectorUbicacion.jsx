import L from 'leaflet'
import { useEffect, useState } from 'react'

import { t } from '../textos/index.js'
import { ESTILOS } from '../util/colores.js'
import MapaBase from './MapaBase.jsx'

// Elegir un punto tocando el mapa o escribiendo las coordenadas
export default function SelectorUbicacion({
  ciudad,
  valor,
  alCambiar,
  ayuda,
  color = ESTILOS.actuar.color,
}) {
  const [mapa, setMapa] = useState(null)

  useEffect(() => {
    if (!mapa) return
    const alTocar = (e) =>
      alCambiar({ lat: +e.latlng.lat.toFixed(5), lon: +e.latlng.lng.toFixed(5) })
    mapa.on('click', alTocar)
    return () => mapa.off('click', alTocar)
  }, [mapa, alCambiar])

  useEffect(() => {
    if (!mapa || !valor) return
    const marca = L.circleMarker([valor.lat, valor.lon], {
      radius: 10,
      color,
      weight: 3,
      fillOpacity: 0.4,
    }).addTo(mapa)
    return () => marca.remove()
  }, [mapa, valor, color])

  function cambiarCampo(campo, texto) {
    const numero = Number(texto)
    if (texto !== '' && Number.isFinite(numero)) {
      alCambiar({ ...(valor ?? { lat: ciudad.centro.lat, lon: ciudad.centro.lon }), [campo]: numero })
    }
  }

  return (
    <div className="space-y-2">
      <p className="text-sm text-gray-600">{ayuda}</p>
      <div className="h-72 overflow-hidden rounded-lg border border-gray-300">
        <MapaBase ciudad={ciudad} alListo={setMapa} />
      </div>
      <div className="grid grid-cols-2 gap-2">
        {[
          ['lat', t.alerta.latitud],
          ['lon', t.alerta.longitud],
        ].map(([campo, etiqueta]) => (
          <label key={campo} className="block text-sm">
            {etiqueta}
            <input
              type="number"
              step="0.00001"
              value={valor?.[campo] ?? ''}
              onChange={(e) => cambiarCampo(campo, e.target.value)}
              className="mt-1 block min-h-11 w-full rounded-lg border border-gray-300 px-3"
              required
            />
          </label>
        ))}
      </div>
    </div>
  )
}
