// Íconos simples en SVG (sin dependencias). Acompañan siempre al color: sirven con daltonismo.
const TRAZOS = {
  alerta: 'M12 3 2 21h20L12 3zm0 6v5m0 3v.5',
  explorar: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm5 12 5 5',
  bien: 'M5 12.5 10 17l9-10',
  pregunta: 'M9 9a3 3 0 1 1 4 2.8c-.8.4-1 1-1 1.7V15m0 3v.5',
  equipo: 'M4 19v-1a5 5 0 0 1 5-5h6a5 5 0 0 1 5 5v1M12 10a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z',
  persona: 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 10a7 7 0 0 1 14 0',
  ruta: 'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm12-10a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM6 15V9a4 4 0 0 1 4-4h6M18 9v6a4 4 0 0 1-4 4H8',
  bloqueo: 'M4 4l16 16M20 4 4 20',
  senal: 'M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0M12 19v.5',
  sinSenal: 'M3 3l18 18M8.5 15.5a5 5 0 0 1 7 0M12 19v.5M5 12a10 10 0 0 1 4-2.3',
  ubicacion: 'M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11zm0-8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  cerrar: 'M6 6l12 12M18 6 6 18',
  mapa: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2zm0 0v14m6-12v14',
  lista: 'M8 6h13M8 12h13M8 18h13M3 6h.5M3 12h.5M3 18h.5',
  mas: 'M12 5v14M5 12h14',
}

export default function Icono({ nombre, className = 'size-5', titulo }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden={titulo ? undefined : true}
      role={titulo ? 'img' : undefined}
    >
      {titulo && <title>{titulo}</title>}
      <path d={TRAZOS[nombre] ?? TRAZOS.pregunta} />
    </svg>
  )
}
