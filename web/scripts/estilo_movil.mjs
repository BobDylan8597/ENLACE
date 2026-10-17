// Genera el estilo del mapa de la app Android (MapLibre) y descarga sus fuentes e íconos para que
// el mapa funcione sin internet. Es el mismo diseño que la web (Protomaps, "light").
// El estilo no depende de la ciudad: la app reemplaza {MAPA} por el mapa descargado del servidor.
//
// Uso (una sola vez, con internet): node scripts/estilo_movil.mjs
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import { layers, namedFlavor } from '@protomaps/basemaps'

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const DESTINO = join(RAIZ, 'movil', 'app', 'src', 'main', 'assets', 'mapa')
const ASSETS = 'https://protomaps.github.io/basemaps-assets'
// Latín básico y extendido y puntuación: alcanza para español y portugués
const RANGOS = ['0-255', '256-511', '8192-8447']
const IDIOMAS = ['es', 'pt']
const ATRIBUCION = '© OpenStreetMap · Protomaps'

async function descargar(url, archivo) {
  const respuesta = await fetch(url)
  if (!respuesta.ok) throw new Error(`${url}: ${respuesta.status}`)
  await mkdir(dirname(archivo), { recursive: true })
  await writeFile(archivo, Buffer.from(await respuesta.arrayBuffer()))
}

// text-font puede ser una lista o una expresión ("case", "coalesce"…): se juntan los nombres
function nombresDeFuentes(valor, fuentes) {
  if (typeof valor === 'string' && valor.startsWith('Noto')) fuentes.add(valor)
  else if (Array.isArray(valor)) for (const v of valor) nombresDeFuentes(v, fuentes)
}

const fuentes = new Set()
for (const idioma of IDIOMAS) {
  const capas = layers('protomaps', namedFlavor('light'), { lang: idioma })
  for (const capa of capas) nombresDeFuentes(capa.layout?.['text-font'], fuentes)
  const estilo = {
    version: 8,
    glyphs: 'asset://mapa/fuentes/{fontstack}/{range}.pbf',
    sprite: 'asset://mapa/sprites/light',
    sources: {
      protomaps: { type: 'vector', url: 'pmtiles://{MAPA}', attribution: ATRIBUCION },
    },
    layers: capas,
  }
  await mkdir(DESTINO, { recursive: true })
  await writeFile(join(DESTINO, `estilo_${idioma}.json`), JSON.stringify(estilo))
  console.log(`estilo_${idioma}.json: ${capas.length} capas`)
}

for (const fuente of fuentes) {
  for (const rango of RANGOS) {
    const ruta = `${encodeURIComponent(fuente)}/${rango}.pbf`
    await descargar(`${ASSETS}/fonts/${ruta}`, join(DESTINO, 'fuentes', fuente, `${rango}.pbf`))
  }
}
console.log(`fuentes: ${[...fuentes].join(', ')}`)
for (const extension of ['json', 'png', '@2x.json', '@2x.png']) {
  const nombre = `light${extension.startsWith('@') ? extension : `.${extension}`}`
  await descargar(`${ASSETS}/sprites/v4/${nombre}`, join(DESTINO, 'sprites', nombre))
}
console.log('íconos: listos')
