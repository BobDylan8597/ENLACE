import asyncio
import threading

from app.eventos.bus import BusEventos, Datos, Tema


def test_manejador_sincrono_recibe_datos() -> None:
    bus = BusEventos()
    recibidos: list[Datos] = []
    bus.suscribir(Tema.reporte_recibido, recibidos.append)
    bus.publicar(Tema.reporte_recibido, {"id": "m-1"})
    bus.publicar(Tema.checkin_recibido, {"id": "otro tema"})
    assert recibidos == [{"id": "m-1"}]


def test_un_manejador_que_falla_no_detiene_a_los_demas() -> None:
    bus = BusEventos()
    recibidos: list[Datos] = []

    def falla(_: Datos) -> None:
        raise RuntimeError("falla a propósito")

    bus.suscribir(Tema.reporte_recibido, falla)
    bus.suscribir(Tema.reporte_recibido, recibidos.append)
    bus.publicar(Tema.reporte_recibido, {"id": "m-1"})
    assert recibidos == [{"id": "m-1"}]


def test_manejador_asincrono_dentro_del_bucle() -> None:
    async def escenario() -> list[Datos]:
        bus = BusEventos()
        recibidos: list[Datos] = []

        async def manejador(datos: Datos) -> None:
            recibidos.append(datos)

        bus.suscribir(Tema.evento_emitido, manejador)
        bus.publicar(Tema.evento_emitido, {"id": 1})
        await asyncio.sleep(0)
        return recibidos

    assert asyncio.run(escenario()) == [{"id": 1}]


def test_publicar_desde_otro_hilo_usa_el_bucle_principal() -> None:
    async def escenario() -> list[Datos]:
        bus = BusEventos()
        bus.vincular_bucle(asyncio.get_running_loop())
        recibido = asyncio.Event()
        recibidos: list[Datos] = []

        async def manejador(datos: Datos) -> None:
            recibidos.append(datos)
            recibido.set()

        bus.suscribir(Tema.celda_actualizada, manejador)
        hilo = threading.Thread(target=bus.publicar, args=(Tema.celda_actualizada, {"h3": "x"}))
        hilo.start()
        await asyncio.wait_for(recibido.wait(), timeout=2)
        hilo.join()
        return recibidos

    assert asyncio.run(escenario()) == [{"h3": "x"}]
