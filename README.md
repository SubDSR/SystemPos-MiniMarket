# Sistema POS MiniMarket - Segundo Entregable

Este proyecto implementa un Sistema POS para minimarket usando Java SE 17+ puro. La aplicacion fue evolucionada desde un primer entregable local hacia una arquitectura Cliente/Servidor real: los clientes ya no guardan datos en archivos locales, sino que se comunican por TCP/IP con un servidor de aplicaciones. Ese servidor concentra las reglas de negocio, valida las operaciones, controla el stock y guarda todo en una base SQLite centralizada.

El proyecto tambien incorpora una parte analitica. A partir de la base transaccional `SERVIDOR_DATOS/minimarket.db`, se genera un Data Warehouse en `SERVIDOR_DWH/data/dwh.db`. Luego se construyen cubos OLAP binarios `.ctab` usando `RandomAccessFile` y calculo manual de offsets. Esto permite demostrar el paso desde una arquitectura transaccional Cliente/Servidor hacia un entorno analitico con DWH y procesamiento tipo cluster simulado por threads.

No se usan frameworks. No hay Spring Boot, Hibernate, REST, JSON, XML, Maven, Gradle, Hadoop ni Spark. La comunicacion entre cliente y servidor se hace con `java.net.Socket` y `java.net.ServerSocket`.

## Estructura General

La carpeta `CLIENTE_POS` contiene el codigo fuente del cliente Swing. Este codigo solo sirve para compilar el JAR del cliente. Las carpetas `CLIENTE_01` y `CLIENTE_02` representan terminales de venta; estas carpetas no tienen codigo fuente ni base de datos, solo contienen `acceso_pos.bat`, que ejecuta el JAR publicado en `SERVIDOR_APLICACIONES/dist`.

La carpeta `SERVIDOR_APLICACIONES` contiene el servidor TCP multihilo. Ahi viven `AppServer.java`, `ClientHandler.java`, los servicios de negocio y el `DatabaseManager`. La carpeta `SERVIDOR_DATOS` contiene la base operativa centralizada `minimarket.db`. Finalmente, `SERVIDOR_DWH` contiene el ecosistema analitico: ETL, configuracion del cluster, metadata de cubos, DWH SQLite y archivos `.ctab`.

```text
SystemPos-MiniMarket/
|-- CLIENTE_POS/
|-- CLIENTE_01/
|-- CLIENTE_02/
|-- SERVIDOR_APLICACIONES/
|-- SERVIDOR_DATOS/
|-- SERVIDOR_DWH/
|-- build_all.bat
|-- run_transaccional.bat
`-- run_cluster_analytics.bat
```

## Archivos .bat

`build_all.bat` compila todo el proyecto. Primero recopila los `.java` del cliente y los compila en `out/cliente`. Luego recopila los `.java` del servidor y los compila en `out/servidor` usando `lib/sqlite-jdbc.jar` en el classpath. Finalmente compila los programas del DWH en `out/dwh`. Al terminar empaqueta `POSClient.jar` y `Server.jar` dentro de `SERVIDOR_APLICACIONES/dist`.

```bat
build_all.bat
```

`run_transaccional.bat` inicia la parte operativa del sistema. Primero arranca `Server.jar`, que abre el puerto TCP `9090`. Despues espera unos segundos y ejecuta dos clientes POS. Esos clientes no guardan nada localmente; simplemente abren ventanas Swing y envian sus operaciones al servidor por sockets.

```bat
run_transaccional.bat
```

`CLIENTE_01/acceso_pos.bat` y `CLIENTE_02/acceso_pos.bat` simulan accesos directos de red. Cada uno intenta ejecutar primero `\\%COMPUTERNAME%\APLICACIONES\POSClient.jar`. Si esa ruta compartida no existe, usa como respaldo `SERVIDOR_APLICACIONES\dist\POSClient.jar`. Esto representa el caso academico donde el cliente no instala la aplicacion, sino que ejecuta el JAR desde el servidor de aplicaciones.

```bat
CLIENTE_01\acceso_pos.bat
CLIENTE_02\acceso_pos.bat
```

`run_cluster_analytics.bat` ejecuta la parte analitica. Primero corre el ETL con `GenerarDatawareHouse`, luego construye los cubos con `CreateCrossTab`, y al final usa `ViewCrossTab` para imprimir en consola los cubos `ventas_2d.ctab` y `ventas_3d.ctab`.

```bat
run_cluster_analytics.bat
```

## Flujo Transaccional

Cuando se abre el POS, `MainApp` verifica que el servidor este vivo enviando un `PING` al AppServer. Si el servidor no responde, el cliente muestra un mensaje de error y no abre la ventana principal. Esto evita que el usuario trabaje en una pantalla que no podria guardar datos.

Cuando el usuario entra a las ventanas de productos, clientes, ventas o historial, la interfaz Swing no accede a SQLite. Cada ventana usa servicios cliente como `ProductoService`, `ClienteService`, `VentaService` o `SucursalService`. Estos servicios son stubs: no contienen persistencia local, solo preparan una peticion de texto y la envian por `SocketClient`.

El flujo de una operacion normal es el siguiente:

```text
Ventana Swing
  -> Service del cliente
  -> SocketClient
  -> TCP/IP puerto 9090
  -> AppServer
  -> ClientHandler en un thread independiente
  -> Service del servidor
  -> DatabaseManager
  -> SQLite centralizado
  -> respuesta OK o ERROR
```

Por eso es normal que en la consola del servidor aparezcan varias peticiones aunque el usuario solo este navegando por ventanas. Por ejemplo, al abrir `Ventas`, el cliente puede pedir productos, clientes y sucursales. Cada consulta abre un socket, envia una linea, recibe respuesta y cierra el socket.

## TCP/IP Y Sockets

La comunicacion usa sockets TCP. El cliente abre una conexion nueva por cada operacion, envia una linea en UTF-8 y espera una sola linea de respuesta. Esta forma es simple, clara y suficiente para demostrar Cliente/Servidor sin introducir REST ni frameworks.

En el cliente, `SocketClient.enviar(...)` crea un `Socket` contra el host y puerto configurados. Por defecto se conecta a `localhost:9090`. Usa `PrintWriter` para enviar la peticion y `BufferedReader` para leer la respuesta.

```java
try (Socket s = new Socket(Config.APP_SERVER_HOST, Config.APP_SERVER_PORT);
     PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
    out.println(peticion);
    return in.readLine();
}
```

En el servidor, `AppServer` crea un `ServerSocket` en el puerto `9090`. Cada vez que llega una conexion, `accept()` devuelve un `Socket`. Ese socket se entrega a un `ClientHandler`, que corre en un thread independiente. Asi, varios clientes pueden consultar o registrar ventas al mismo tiempo.

```java
ServerSocket ss = new ServerSocket(ServerConfig.PORT);
while (true) {
    Socket c = ss.accept();
    new Thread(new ClientHandler(c, db)).start();
}
```

El servidor ahora registra logs mas descriptivos. En vez de mostrar solo `Cliente conectado`, cada linea indica la IP, el hilo, la operacion, la entidad, el resultado y el tiempo de ejecucion. Un ejemplo esperado es:

```text
2026-05-31 22:50:10 | INFO    | ClientHandler  | ip=127.0.0.1:53210 hilo=client-53210 op=LISTAR entidad=PRODUCTO estado=OK codigo=OK ms=8
2026-05-31 22:50:15 | INFO    | ClientHandler  | ip=127.0.0.1:53211 hilo=client-53211 op=REGISTRAR entidad=VENTA estado=OK codigo=OK ms=31
```

Los logs tambien se guardan en archivo dentro de `SERVIDOR_APLICACIONES/logs`, con nombre diario como `server_2026-05-31.log`.

## Protocolo De Red

El protocolo es texto plano. Cada mensaje usa `|` como separador. No se permite que los campos contengan `|`, porque eso romperia el parseo de la peticion.

Formato de peticion:

```text
OPERACION|ENTIDAD|campo1|campo2|...
```

Formato de respuesta exitosa:

```text
OK|mensaje
OK|id_generado
OK|dato1;dato2;dato3
```

Formato de respuesta con error:

```text
ERROR|codigo|mensaje
```

Algunas operaciones implementadas son `PING`, `LISTAR|PRODUCTO`, `LISTAR|CLIENTE`, `LISTAR|SUCURSAL`, `CREAR|PRODUCTO`, `CREAR|CLIENTE`, `REGISTRAR|VENTA`, `HISTORIAL|VENTA`, `DETALLES|VENTA` y `ANULAR|VENTA`.

Una venta se registra con este formato:

```text
REGISTRAR|VENTA|clienteId|sucursalId|prodId:cantidad;prodId:cantidad
```

Por ejemplo:

```text
REGISTRAR|VENTA|1|2|3:1;5:2
```

Esa peticion significa que el cliente `1` registra una venta en la sucursal `2` con una unidad del producto `3` y dos unidades del producto `5`.

## Guardado En SQLite

El guardado ocurre solamente en el servidor. El cliente no tiene acceso a `minimarket.db`. Cuando una ventana necesita guardar un producto, cliente o venta, envia la peticion al AppServer. El AppServer valida el mensaje, llama al servicio correspondiente y finalmente `DatabaseManager` ejecuta SQL mediante JDBC.

La base operativa esta en:

```text
SERVIDOR_DATOS\minimarket.db
```

Las tablas principales son `sucursales`, `productos`, `clientes`, `ventas` y `detalle_ventas`. Las sucursales base son Lima, Arequipa y Trujillo. En la ventana de ventas el usuario elige la sucursal, y ese valor se guarda en la columna `ventas.id_sucursal`.

El caso mas importante es `registrarVenta`. Ese metodo esta marcado como `synchronized`, porque afecta stock y debe ser seguro si dos clientes venden al mismo tiempo. Internamente abre una transaccion JDBC. Primero valida que la sucursal exista, valida el cliente si corresponde, verifica que todos los productos existan y que haya stock suficiente. Despues inserta la cabecera en `ventas`, inserta las lineas en `detalle_ventas` y descuenta el stock en `productos`.

Si todo sale bien, se hace `COMMIT` y el servidor responde `OK|idVenta`. Si algo falla, se hace `ROLLBACK` y el servidor responde `ERROR|TX|mensaje`. Esto evita ventas incompletas o descuentos de stock sin detalle asociado.

## Seleccion De Sucursal

La ventana `Ventas` tiene un selector de sucursal. La lista se obtiene desde el servidor con `LISTAR|SUCURSAL`, por lo que el cliente no tiene las sucursales codificadas localmente como base de datos. Las sucursales disponibles son:

```text
1 - Lima
2 - Arequipa
3 - Trujillo
```

Cuando se registra la venta, el ID seleccionado viaja como `sucursalId` dentro de `REGISTRAR|VENTA`. Luego se puede ver en `Dashboard` e `Historial` como `S#1`, `S#2` o `S#3`. Ese mismo campo es el que permite construir el cubo 3D por sucursal.

## Data Warehouse

El DWH se genera desde la base operativa. No se alimenta con CSV ni con sockets. El proceso `GenerarDatawareHouse` abre por JDBC `SERVIDOR_DATOS/minimarket.db`, lee las tablas operativas y crea una version analitica en `SERVIDOR_DWH/data/dwh.db`.

El DWH usa un esquema estrella. Las dimensiones son `DIM_PRODUCTO`, `DIM_CLIENTE`, `DIM_TIEMPO` y `DIM_SUCURSAL`. La tabla central es `FACT_VENTAS`, donde cada fila representa una linea vendida con producto, cliente, tiempo, sucursal, cantidad, total vendido e IGV.

Cuando se dice que los datos se suben al SQLite analitico, significa que el ETL copia y transforma datos desde el SQLite operativo hacia otro SQLite especializado para analisis. No se esta copiando el archivo completo. Se leen registros transaccionales y se insertan registros preparados para consultas analiticas.

El comando equivalente del ETL es:

```bat
"%JAVA_HOME%\bin\java.exe" -cp "out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar" GenerarDatawareHouse SERVIDOR_DATOS\minimarket.db SERVIDOR_DWH\data\dwh.db
```

## Cubos .ctab

Despues del ETL, `CreateCrossTab` lee `SERVIDOR_DWH/data/dwh.db`, `metadata.properties` y `Assign.txt`. La metadata indica que el cubo usa Producto como dimension 1, Mes como dimension 2 y Sucursal como dimension 3. `Assign.txt` simula la distribucion de trabajo entre nodos: cada linea asigna un conjunto de productos a un hostname o IP.

El programa crea un thread por nodo definido en `Assign.txt`. Cada thread consulta `FACT_VENTAS`, acumula valores y escribe en archivos binarios `.ctab`. Los archivos generados son:

```text
SERVIDOR_DWH\repositorio_analitico\ventas_2d.ctab
SERVIDOR_DWH\repositorio_analitico\ventas_3d.ctab
```

`ventas_2d.ctab` representa Producto x Mes. `ventas_3d.ctab` representa Producto x Mes x Sucursal. Cada celda del cubo es un `double`, por eso ocupa 8 bytes.

La posicion de una celda no se busca por nombre. Se calcula matematicamente. Para el cubo 2D se usa:

```java
long offset2D = ((long)(i - 1) * N + (j - 1)) * W;
```

Para el cubo 3D se usa:

```java
long offset3D = ((long)(i1 - 1) * N * P + (i2 - 1) * P + (i3 - 1)) * W;
```

Luego se hace `raf.seek(offset)`, se lee el valor anterior y se escribe el acumulado. Este es el punto clave del almacenamiento OLAP binario: el archivo no se recorre completo para ubicar una celda, sino que se salta directamente a su posicion.

## Como Ver Los .ctab

Los archivos `.ctab` son binarios. No deben abrirse con Bloc de notas porque se veran como caracteres raros. Para visualizarlos se usa `ViewCrossTab`, que calcula los offsets, lee los `double` y los imprime como matriz en consola.

En PowerShell, desde la raiz del proyecto:

```powershell
cd C:\Proyectos\SystemPos-MiniMarket
& "$env:JAVA_HOME\bin\java.exe" -cp "out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar" ViewCrossTab ventas_2d
& "$env:JAVA_HOME\bin\java.exe" -cp "out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar" ViewCrossTab ventas_3d
```

En CMD, desde la raiz del proyecto:

```bat
cd /d C:\Proyectos\SystemPos-MiniMarket
"%JAVA_HOME%\bin\java.exe" -cp "out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar" ViewCrossTab ventas_2d
"%JAVA_HOME%\bin\java.exe" -cp "out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar" ViewCrossTab ventas_3d
```

Tambien se puede ejecutar el flujo completo con:

```bat
run_cluster_analytics.bat
```

El nombre correcto de los cubos es `.ctab`. Si se menciona `.cbat`, es un error de escritura.

## Salida Esperada

`ventas_2d` se imprime como una matriz de productos contra meses. Cada fila es un producto y cada columna es un mes. Si hay pocas ventas, la mayoria de celdas aparecera como `0.00`.

```text
Producto\Mes                 1         2         3 ...        12
Producto_1                0.00      0.00      0.00 ...      5.66
Producto_2                0.00      0.00      0.00 ...      4.96
```

`ventas_3d` repite esa matriz por cada sucursal.

```text
Sucursal_1
Producto\Mes                 1         2         3 ...        12
Producto_1                0.00      0.00      0.00 ...      5.66

Sucursal_2
Producto\Mes                 1         2         3 ...        12
Producto_1                0.00      0.00      0.00 ...      0.00
```

## Uso Recomendado

Primero compila todo con `build_all.bat`. Despues ejecuta `run_transaccional.bat` para abrir el servidor y los clientes. Desde el POS puedes crear productos, clientes y registrar ventas seleccionando una sucursal. Cuando ya existan ventas, ejecuta `run_cluster_analytics.bat` para generar el DWH y visualizar los cubos.

Si el sistema muestra error de version Java, revisa que `JAVA_HOME` apunte a un JDK 17 o superior. En PowerShell puedes verificarlo con:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version
```
