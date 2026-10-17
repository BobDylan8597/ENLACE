// Textos de la interfaz en español. Para portugués: crear pt.js con las mismas claves.
// En oración (sentence case), cortos y sin tecnicismos (CLAUDE.md, sección 16).

const es = {
  producto: 'ENLACE',
  subtitulo: 'Centro de coordinación',

  ingreso: {
    titulo: 'Ingresar al centro de coordinación',
    detalle: 'Para operadores y coordinadores de los organismos de respuesta.',
    lema: 'Decidir cuando todavía no se ve nada, y corregir con cada dato que llega.',
    puntos: [
      'Alertas firmadas que llegan aunque se caiga internet',
      'Prioridades por zona desde el primer minuto',
      'Equipos asignados con ruta y explicación de cada decisión',
    ],
    pie: 'Rally Latinoamericano de Innovación · Reto 09',
    clave: 'Clave de acceso',
    nombre: 'Tu nombre',
    nombreAyuda: 'Queda registrado en cada decisión que tomes',
    entrar: 'Entrar',
    incorrecta: 'La clave no es correcta',
    sinServidor: 'No se pudo conectar con el servidor ENLACE',
  },

  navegacion: {
    reportes: 'Centro de reportes',
    tablero: 'Mapa',
    alerta: 'Emitir alerta',
    equipos: 'Equipos',
    manual: 'Reporte manual',
    asignaciones: 'Asignaciones',
    bloqueos: 'Bloqueos',
    paquete: 'Primera hora',
    salir: 'Salir',
    operador: 'Operador',
    grupos: { operacion: 'Operación', coordinacion: 'Coordinación' },
  },

  conexion: {
    conectado: 'En línea',
    reconectando: 'Reconectando…',
    desconectado: 'Sin conexión con el servidor',
  },

  evento: {
    sinEvento: 'Sin alerta activa',
    activo: (tipo, magnitud) => `Alerta activa: ${tipo}${magnitud ? ` M${magnitud}` : ''}`,
  },

  tipoEvento: { sismo: 'Sismo', inundacion: 'Inundación', huracan: 'Huracán', otro: 'Otro' },

  categoria: {
    actuar: 'Actuar',
    explorar: 'Explorar',
    estable: 'Estable',
    explorar_despues: 'Explorar después',
    sin_datos: 'Sin datos',
  },

  tipoReporte: { ayuda: 'Pide ayuda', bien: 'Está bien', evaluacion: 'Evaluación' },
  origen: { app: 'App', sms: 'SMS', nearby: 'Red cercana', radio: 'Radio', brigada: 'Brigada' },
  estadoReporte: {
    sin_asignar: 'Sin asignar',
    asignado: 'Asignado',
    atendido: 'Atendido',
    descartado: 'Descartado',
  },
  necesidad: { rescate: 'Rescate', medico: 'Médico', agua: 'Agua', refugio: 'Refugio' },
  nivelDano: {
    ninguno: 'Sin daño',
    leve: 'Leve',
    moderado: 'Moderado',
    grave: 'Grave',
    colapso: 'Colapso',
  },
  capacidad: {
    rescate: 'Rescate',
    medico: 'Médico',
    agua: 'Agua',
    reconocimiento: 'Reconocimiento',
    maquinaria: 'Maquinaria',
  },
  estadoEquipo: { disponible: 'Disponible', en_ruta: 'En ruta', ocupado: 'Ocupado' },
  decision: {
    pendiente: 'Sugerida',
    aceptada: 'Aceptada',
    modificada: 'Equipo cambiado',
    rechazada: 'Rechazada',
  },
  estadoBloqueo: {
    sin_confirmar: 'Sin confirmar',
    probable: 'Probable',
    confirmado: 'Confirmado',
    descartado: 'Descartado',
  },
  tipoBloqueo: {
    derrumbe: 'Derrumbe',
    agua: 'Agua',
    escombros: 'Escombros',
    cables: 'Cables caídos',
    via_rota: 'Vía rota',
    otro: 'Otro',
  },

  indicadores: {
    afectadas: 'Personas afectadas',
    criticos: 'Vulnerables sin responder',
    pedidos: 'Pedidos sin asignar',
    equipos: 'Equipos disponibles',
  },

  reportes: {
    titulo: 'Centro de reportes',
    detalle: 'Pedidos y evaluaciones ordenados por prioridad. Elige uno para ver el equipo sugerido.',
    vacio: 'No hay reportes por ahora',
    sinEvento: 'Cuando haya una alerta activa, los reportes aparecerán aquí por prioridad',
    filtros: { todos: 'Todos', estado: 'Estado', tipo: 'Tipo', origen: 'Origen' },
    atrapados: (n) => `${n} ${n === 1 ? 'atrapado' : 'atrapados'}`,
    heridos: (n) => `${n} ${n === 1 ? 'herido' : 'heridos'}`,
    personas: (n) => `${n} ${n === 1 ? 'persona' : 'personas'}`,
    vulnerables: (n) => `${n} en situación vulnerable`,
    duplicados: (n) => `+${n} ${n === 1 ? 'reenvío' : 'reenvíos'}`,
    puntoExacto: 'Ubicación exacta',
    actualizado: 'Información nueva',
    seleccionar: 'Elige un reporte para ver el detalle',
    bandeja: 'Bandeja',
  },

  detalle: {
    prioridad: 'Prioridad',
    porQue: 'Por qué tiene esta prioridad',
    componentes: {
      gravedad: 'Gravedad',
      vulnerabilidad: 'Vulnerabilidad',
      espera: 'Tiempo de espera',
      celda: 'Situación de la zona',
      impacto: 'Impacto estimado',
      urgencia: 'Horas desde el evento',
    },
    persona: 'Persona que pidió ayuda',
    nombre: 'Nombre',
    telefono: 'Teléfono',
    condiciones: 'Condiciones',
    sinDatosPersonales: 'Sin datos de contacto',
    sugerido: 'Equipo sugerido',
    asignar: 'Asignar',
    cambiarEquipo: 'Cambiar equipo',
    elegirEquipo: 'Elige otro equipo',
    confirmarCambio: 'Asignar este equipo',
    rechazar: 'Rechazar sugerencia',
    sinSugerencia: 'No hay un equipo disponible con la capacidad necesaria',
    actualizadoTrasAsignar:
      'La persona envió información nueva después de la asignación: revisa si el equipo sigue siendo el adecuado.',
    llegaEn: (min) => `Llega en unos ${Math.round(min)} min`,
    historial: 'Asignaciones',
    marcarAtendido: 'Marcar atendido',
    decididoPor: 'Decidido por',
    ruta: 'Ruta',
    cerrar: 'Cerrar',
  },

  tablero: {
    titulo: 'Mapa de la emergencia',
    capas: 'Capas',
    capa: {
      celdas: 'Zonas por prioridad',
      criticos: 'Población crítica',
      personas: 'Personas ubicadas',
      checkins: 'Check-ins por zona',
      ayuda: 'Pedidos de ayuda',
      equipos: 'Equipos',
      bloqueos: 'Bloqueos',
      rutas: 'Rutas en curso',
    },
    sinMapa: 'Sin mapa base: ejecutar la preparación de la ciudad',
    leyenda: 'Leyenda',
    conVulnerables: 'Hogar con personas que necesitan atención especial',
    privacidadPersonas:
      'Solo aparecen quienes eligieron compartir su ubicación exacta. Se borra al cerrar la emergencia.',
  },

  // Infraestructura crítica por celda (OpenStreetMap)
  infraestructura: {
    hospital: (n) => `${n} ${n === 1 ? 'hospital' : 'hospitales'}`,
    clinica: (n) => `${n} ${n === 1 ? 'clínica' : 'clínicas'}`,
    escuela: (n) => `${n} ${n === 1 ? 'escuela' : 'escuelas'}`,
    bomberos: (n) => `${n} ${n === 1 ? 'estación de bomberos' : 'estaciones de bomberos'}`,
    policia: (n) => `${n} ${n === 1 ? 'estación de policía' : 'estaciones de policía'}`,
    albergue: (n) => `${n} ${n === 1 ? 'albergue' : 'albergues'}`,
  },

  estadoPersona: {
    sin_respuesta: 'Sin respuesta',
    ayuda: 'Pidió ayuda',
    bien: 'Está bien',
  },

  celda: {
    titulo: 'Detalle de la zona',
    impacto: 'Personas afectadas (estimado)',
    inicial: 'Estimación inicial del modelo',
    certeza: 'Certeza',
    vulnerabilidad: 'Vulnerabilidad',
    reportes: 'Reportes',
    checkins: 'Check-ins',
    infraestructura: 'Infraestructura crítica',
    ultimaAtencion: 'Última atención',
    nunca: 'Aún no atendida',
    totales: (c) => `${c.bien} bien · ${c.ayuda} piden ayuda · ${c.sin_respuesta} sin respuesta`,
    personas: 'Personas en la zona (informadas)',
    vulnerables: 'Necesitan atención especial',
    criticos: 'Población crítica',
    poblacion: (c) =>
      `${c.criticos} ${c.criticos === 1 ? 'persona vulnerable' : 'personas vulnerables'} sin responder o pidiendo ayuda · ` +
      `${c.personas} personas informadas`,
    hogar: (u) =>
      `${u.personas} ${u.personas === 1 ? 'persona' : 'personas'}` +
      (u.vulnerables > 0 ? ` · ${u.vulnerables} con atención especial` : ''),
  },

  alerta: {
    titulo: 'Emitir alerta',
    detalle: 'Siempre con confirmación humana. La alerta sale firmada: los celulares descartan cualquier otra.',
    activa: 'Ya hay una alerta activa',
    tipo: 'Tipo de evento',
    magnitud: 'Magnitud',
    profundidad: 'Profundidad (km)',
    ubicacion: 'Ubicación del evento',
    ubicacionAyuda: 'Toca el mapa para marcar el epicentro o escribe las coordenadas',
    latitud: 'Latitud',
    longitud: 'Longitud',
    revisar: 'Revisar alerta',
    confirmarTitulo: 'Confirma la alerta',
    confirmarTexto: 'Se enviará a todos los celulares de la zona y no se puede deshacer.',
    emitir: 'Emitir alerta ahora',
    volver: 'Volver',
    emitida: 'Alerta emitida y firmada',
    cerrarEvento: 'Cerrar evento',
    confirmarCierre: 'Al cerrar, los datos personales se borrarán en el plazo configurado.',
    cerrarAhora: 'Cerrar evento ahora',
    propuestas: 'Propuestas de la comunidad',
    sinPropuestas: 'No hay propuestas pendientes',
    propuesta: (n, zona) => `${n} personas piden ayuda en ${zona}`,
    aprobar: 'Emitir alerta',
    descartar: 'Descartar',
  },

  equipos: {
    titulo: 'Equipos',
    detalle: 'Equipos de respuesta registrados, sus capacidades y si están disponibles.',
    nuevo: 'Registrar equipo',
    nombre: 'Nombre',
    organismo: 'Organismo',
    capacidades: 'Capacidades',
    estado: 'Estado',
    ubicacion: 'Ubicación',
    ubicacionAyuda: 'Toca el mapa para marcar dónde está el equipo',
    registrar: 'Registrar',
    vacio: 'No hay equipos registrados',
  },

  manual: {
    titulo: 'Reporte manual',
    ayuda: 'Para reportes recibidos por teléfono, radio o en persona.',
    origen: 'Cómo llegó',
    tipo: 'Tipo de reporte',
    ubicacion: 'Ubicación',
    ubicacionAyuda: 'Toca el mapa para marcar dónde está la persona',
    personas: 'Personas',
    atrapados: 'Atrapados',
    heridos: 'Heridos',
    vulnerables: 'Vulnerables',
    necesidades: 'Necesidades',
    nivelDano: 'Nivel de daño',
    nombre: 'Nombre (opcional)',
    telefono: 'Teléfono (opcional)',
    enviar: 'Enviar reporte',
    enviado: 'Reporte registrado',
    repetido: 'Este reporte ya estaba registrado',
  },

  asignaciones: {
    titulo: 'Asignaciones en curso',
    explicacion: 'Equipos en camino o trabajando en una zona. Se cierran cuando la unidad marca la zona atendida.',
    enCamino: 'En curso',
    decidida: (quien, cuando) => `Decidida por ${quien ?? 'el centro'} · ${cuando}`,
    sinRuta: 'Sin ruta por calles con los bloqueos actuales',
    verPedido: 'Ver el pedido',
    vacio: 'No hay asignaciones en curso.',
  },

  bloqueos: {
    titulo: 'Calles bloqueadas',
    explicacion:
      'Reportes de la comunidad y de las brigadas. Confirmar un bloqueo lo saca de las rutas; descartarlo las restaura.',
    aPie: 'se puede pasar a pie',
    reportes: (n) => `${n} ${n === 1 ? 'reporte' : 'reportes'}`,
    porBrigada: 'confirmado por una brigada',
    sinCalle: 'lejos de las calles: no cambia rutas',
    confirmar: 'Confirmar',
    descartar: 'Descartar',
    vacio: 'No hay bloqueos activos.',
  },

  paquete: {
    titulo: 'Paquete de primera hora',
    explicacion:
      'Una hoja para imprimir o compartir con los organismos: qué pasó, dónde actuar primero y con qué equipos.',
    imprimir: 'Imprimir',
    sinEvento: 'No hay una emergencia activa.',
    resumen: (personas, criticos, zonas) =>
      `Unas ${personas.toLocaleString('es')} personas afectadas según el modelo, ${criticos} personas vulnerables sin responder, ${zonas} zonas en el área afectada.`,
    zonas: 'Zonas por prioridad',
    zona: 'Zona',
    categoria: 'Situación',
    primerasAcciones: 'Primeras acciones recomendadas',
    sinRecomendaciones: 'Sin recomendaciones pendientes.',
    equipos: (disponibles, total) => `Equipos: ${disponibles} disponibles de ${total}`,
    pie: (fecha) =>
      `Generado por ENLACE el ${fecha}. Estimaciones del modelo corregidas con los reportes recibidos: la decisión final es siempre humana.`,
  },

  comun: {
    cargando: 'Cargando…',
    error: 'Algo salió mal',
    reintentar: 'Reintentar',
    hace: (texto) => `hace ${texto}`,
    ahora: 'recién',
    minutos: (n) => `${n} min`,
    horas: (n) => `${n} h`,
    km: (n) => `${n} km`,
    m: (n) => `${n} m`,
  },
}

export default es
