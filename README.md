# Sonntag

**Español** · [Português](README.pt-BR.md) · [English](README.en.md)

Aplicación de escritorio para organizar los programas de reuniones de una congregación:
discursos del fin de semana, programa de entre semana, publicadores, audio/video,
acomodadores y la escala de limpieza. Todo se guarda en su computadora y cada programa
se puede exportar en PDF o PNG para imprimir o compartir.

> Proyecto independiente, sin vínculo ni respaldo oficial de ninguna organización.

## Características

- **Panel** — próxima reunión, grupo de limpieza de la semana y reuniones cuyo programa
  todavía está incompleto en los próximos 28 días.
- **Programa de fin de semana** — título del discurso, orador, presidente, dirigente del
  estudio y lector. Exporta una reunión o el mes entero (PDF/PNG).
- **Programa de entre semana (S-140)** — formulario completo: tesoros, ministerio, vida
  cristiana, cánticos y oraciones. Exporta el programa e imprime las asignaciones (S-89).
- **Publicadores** — registro de nombres usados en todas las asignaciones.
- **Audio/video y acomodadores** — audio, video, plataforma, micrófonos y acomodadores
  por reunión, con exportación en PDF.
- **Limpieza** — grupo responsable por semana, con exportación mensual en PDF/PNG.
- **Importaciones** — la guía de actividades (`mwb`, en PDF) llena el programa de entre
  semana; el **S-34** (`.jwpub`) trae los 194 bosquejos de discursos públicos a una lista
  de selección en el título del discurso.
- **Sin conexión** — no requiere internet ni cuenta; los datos nunca salen de la máquina.

## Instalación

Descargue el instalador de la versión más reciente en la página de
[Releases](../../releases):

| Sistema | Archivo |
| ------- | ------- |
| Windows | `Sonntag-<versión>.msi` |
| Linux (Debian/Ubuntu) | `sonntag_<versión>_amd64.deb` |

En Windows el instalador crea el acceso directo en el menú Inicio y pregunta si desea
uno en el escritorio. En Linux: `sudo dpkg -i sonntag_<versión>_amd64.deb`.

## Primeros pasos

1. Al abrir el programa por primera vez aparece la **configuración inicial**: nombre de
   la congregación (obligatorio), dirección, teléfono y los **días y horarios de reunión**.
2. Con los días definidos, la aplicación genera las reuniones de los próximos 12 meses.
3. Registre a los **publicadores** y, en Configuración, los **grupos de limpieza**.
4. Opcional: use **Importar S-34** en la pantalla de fin de semana para tener la lista de
   bosquejos, e **Importar guía** en la de entre semana para llenar las semanas del mes.

## Idioma

La aplicación habla **español** y **portugués (BR)**. En el primer inicio adopta el idioma
del sistema operativo; si es otro, usa español. Puede cambiarlo cuando quiera en
**Configuración › General › Idioma**, y esa elección prevalece sobre el sistema. Los
documentos exportados salen en el idioma seleccionado.

## Sus datos

Todo se guarda en un único archivo SQLite:

```
Linux/macOS   ~/.salao-app/data.db
Windows       C:\Users\<usuario>\.salao-app\data.db
```

Para respaldar o mudar de computadora, copie ese archivo. Desinstalar la aplicación no
lo borra.

## Desarrollo

Requiere **JDK 17**. Kotlin Multiplatform con Compose Multiplatform (solo escritorio/JVM).

```shell
./gradlew :composeApp:run            # ejecutar
./gradlew :composeApp:packageDeb     # paquete .deb (en Linux)
./gradlew :composeApp:packageMsi     # instalador .msi (en Windows)
./gradlew :composeApp:packageMsiWithPrompt  # .msi que pregunta por los accesos directos (el del Release)
./gradlew :composeApp:exportAppIcon  # regenerar el ícono desde AppIcon.kt
```

Los instaladores solo se generan en el sistema de destino: jpackage no compila para
otra plataforma.

| Componente | Uso |
| ---------- | --- |
| Compose Multiplatform | interfaz |
| SQLDelight + SQLite (JDBC/HikariCP) | base de datos local |
| Koin | inyección de dependencias |
| Voyager | pantallas |
| Apache PDFBox | exportación en PDF y lectura de la guía `mwb` |

Estructura: `composeApp/src/commonMain` tiene la interfaz, los ViewModels, los
repositorios y las traducciones; `composeApp/src/jvmMain` tiene lo específico del
escritorio (base de datos, PDF, importaciones, ícono, ventana).

### Publicar una versión

El flujo lo dispara el mensaje del commit. Agregue la sección de la versión en
[`CHANGELOG.md`](CHANGELOG.md), haga un commit **`Fecha versão <x.y.z>`** y súbalo a
`master`: el workflow lee la versión del mensaje, valida el changelog, genera el `.msi`
y el `.deb`, crea la etiqueta `v<x.y.z>` y publica el Release con esas notas.

## Licencia

Distribuido bajo la licencia [MIT](LICENSE).
