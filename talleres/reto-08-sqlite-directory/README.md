# Reto 08 — Almacenamiento con SQLite

Taller individual: una app que usa SQLite para gestionar un directorio de
empresas de desarrollo de software. Es una app nueva (no es el triqui), en
Kotlin con MVVM. Del reto 07 se reutilizó la configuración de Gradle, el tema,
los diálogos de tema y Acerca de, y la forma de armar listas y ViewModels.

Lo que pide el enunciado y cómo quedó:

| Enunciado | En la app |
|---|---|
| Nombre, URL, teléfono, email, productos y servicios, y clasificación | Formulario con esos campos. La clasificación son tres chips: consultoría, desarrollo a la medida y fábrica de software (una o varias) |
| CRUD sobre las empresas | **Crear**: "Nueva empresa". **Ver y editar**: tocar una tarjeta abre el formulario con sus datos. **Eliminar**: ícono de basura en ese formulario |
| Listar las empresas | La pantalla inicial es la lista, ordenada por nombre |
| Filtrar por nombre y/o clasificación | Buscador por nombre + chips de clasificación, combinables. Se filtra en SQL mientras se escribe |
| Confirmar antes de eliminar | Diálogo "¿Eliminar la empresa? … no se puede deshacer" |
| SQLite (tutorial con `SQLiteOpenHelper`) | `DirectoryDbHelper extends SQLiteOpenHelper`, con el SQL escrito a mano (sin Room) |

## La base de datos

Archivo `directory.db`, versión 1, dos tablas:

```sql
CREATE TABLE companies (
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL CHECK (length(trim(name)) > 0),
  name_key TEXT NOT NULL UNIQUE,      -- nombre normalizado: sin tildes, minúsculas
  website TEXT, phone TEXT, email TEXT,
  products TEXT,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);

CREATE TABLE company_classifications (
  company_id INTEGER NOT NULL REFERENCES companies(_id) ON DELETE CASCADE,
  classification TEXT NOT NULL
    CHECK (classification IN ('CONSULTING', 'CUSTOM_DEVELOPMENT', 'SOFTWARE_FACTORY')),
  PRIMARY KEY (company_id, classification)
);
```

- **Clasificación "y/o"**: una empresa puede tener varias, así que van en
  una tabla intermedia, con una fila por clasificación. Borrar la empresa
  borra sus filas (`ON DELETE CASCADE`). Las claves foráneas se activan en
  `onConfigure`, porque SQLite las trae apagadas.
- **Nombre único**, sin distinguir mayúsculas, tildes ni espacios de más
  ("Ñandú" = "nandu"). Lo garantiza el `UNIQUE` de `name_key`. Si se repite,
  el formulario muestra "Ya existe una empresa con este nombre".
- **Campos opcionales vacíos** se guardan como `NULL`.
- **Crear y actualizar** van en una transacción. Actualizar reemplaza las
  clasificaciones (borrar + insertar): o queda todo, o nada.
- **`onUpgrade`**: en la versión 1 no hay nada que migrar. Cuando cambie el
  esquema, cada versión agrega su paso (`if (oldVersion < 2) ALTER TABLE …`).
  No se usa el `DROP TABLE` + `onCreate` del tutorial, porque borraría las
  empresas del usuario.
- **Datos de ejemplo**: al crear la base (la primera vez que se abre la
  app) se insertan 6 empresas ficticias (dominios `example.com`). Están
  pensadas para probar el filtro: hay tildes, "ñ" y todas las combinaciones
  de clasificación.

### Filtro

```sql
SELECT c._id, c.name, …, GROUP_CONCAT(cc.classification) AS classifications
FROM companies c
LEFT JOIN company_classifications cc ON cc.company_id = c._id
WHERE c.name_key LIKE ? ESCAPE '\'
  AND EXISTS (SELECT 1 FROM company_classifications f
              WHERE f.company_id = c._id AND f.classification IN (?, ?))
GROUP BY c._id
ORDER BY c.name_key
```

- **Nombre**: "contiene", sin distinguir mayúsculas ni tildes. El `LIKE` de
  SQLite solo ignora mayúsculas en ASCII ("Á" ≠ "á"), por eso se busca sobre
  `name_key`. Lo que escribe el usuario se normaliza igual. `%` y `_` se
  escapan: buscar "100%" busca eso literalmente, no un comodín.
- **Clasificación**: ninguna marcada = todas. Varias = **cualquiera de
  ellas**. Va en un `EXISTS` aparte: si se filtrara sobre el `JOIN`, el
  `GROUP_CONCAT` traería solo las clasificaciones que coinciden.
- **Nombre y clasificación** se combinan con `AND`. Cada parte se agrega
  solo si se usa.
- **Todo valor del usuario va como argumento `?`** o en `ContentValues`,
  nunca pegado al SQL. Lo único que se arma es la cantidad de `?` del `IN`,
  que sale de un enum.
- La consulta corre mientras se escribe, 300 ms después de la última tecla.
  Borrar la búsqueda y marcar chips filtra al instante. Después de crear,
  editar o eliminar, la lista se vuelve a consultar sola.

## Clases (MVVM)

```
co.edu.unal.softwaredirectory
├── DirectoryApp              tema guardado + el repositorio (un solo DbHelper para la app)
├── ThemePreference, AppDialogs    del reto 07: tema claro/oscuro y Acerca de
├── data/
│   ├── Company               Company, Classification, CompanyFilter, SaveResult
│   ├── CompanyRepository     interfaz del CRUD
│   ├── DirectoryContract     nombres de tablas y columnas
│   ├── DirectoryDbHelper     SQLiteOpenHelper: esquema, onConfigure, onCreate, onUpgrade
│   ├── SqliteCompanyRepository   el SQL: consultas, transacciones, hilo de IO
│   ├── SearchText            normaliza texto y arma el patrón LIKE
│   └── SampleData            empresas de ejemplo
└── ui/
    ├── list/   CompanyListActivity, CompanyListViewModel, CompaniesAdapter
    └── form/   CompanyFormActivity, CompanyFormViewModel, CompanyValidator
```

Los ViewModels reciben un `CompanyRepository` (una interfaz). En los tests
de ViewModel reciben un fake en memoria con el mismo filtro y las mismas
reglas.

## Validación

- **Obligatorios**: el nombre (máximo 100 caracteres) y al menos una
  clasificación.
- **Opcionales, pero validados si se llenan**:
  - **Web**: si no trae `http(s)://`, se agrega `https://`. El host tiene
    que tener un punto.
  - **Teléfono**: de 7 a 15 dígitos, con espacios, guiones, paréntesis y
    `+` al principio.
  - **Email**: `nombre@dominio.tld`, se guarda en minúsculas.
- Los errores aparecen en cada campo al guardar y se borran al corregir
  ese campo.

## Extras

- **Abrir la web, llamar y escribir un email** desde los íconos al final de
  esos campos. Solo aparecen si el valor es válido. "Llamar" abre el
  marcador con el número (`ACTION_DIAL`), así que la app no pide permisos.
- **"¿Descartar los cambios?"** al salir del formulario con cambios sin
  guardar (Back o la flecha).
- **Snackbar** en la lista: "Empresa agregada", "Cambios guardados",
  "Empresa eliminada".
- Tema claro/oscuro, Acerca de con el número de reto y versión, textos en
  español e inglés, ícono propio.

## Rotar y matar el proceso

| Caso | Qué pasa |
|---|---|
| Rotar con el formulario a medio llenar | El borrador vive en el ViewModel y no se pierde nada |
| Android mata el proceso con el formulario a medio llenar | El borrador está en el `SavedStateHandle` y vuelve tal cual. Al editar, la empresa se lee de la base **una sola vez**: al volver no se relee, porque eso pisaría lo que el usuario no había guardado |
| Rotar con un diálogo abierto (eliminar, descartar) | El diálogo es parte del estado, así que se vuelve a abrir, también tras la muerte del proceso |
| Rotar o matar el proceso con un filtro puesto | La búsqueda y los chips están en el `SavedStateHandle` |
| La empresa se borró mientras se editaba | Al guardar o abrirla: "Esa empresa ya no existe" y vuelve a la lista |

## Cómo correrlo

1. Android Studio → *Open* → `talleres/reto-08-sqlite-directory`, esperar el
   *Gradle Sync* y darle *Run 'app'*. No hace falta configurar nada: la
   base se crea sola en el teléfono.
2. Para ver la base: *View → Tool Windows → App Inspection → Database
   Inspector*, con la app corriendo.

Tests (no requieren emulador):

```powershell
cd talleres\reto-08-sqlite-directory
.\gradlew.bat testDebugUnitTest
```

La primera vez, Robolectric descarga el jar de Android 17 (~240 MB) a
`~/.m2`.

### Guion de prueba

1. Abrir la app: aparecen las 6 empresas de ejemplo.
2. Buscar "bogota" (sin tilde) → aparece "Soluciones Bogotá Cloud". Buscar
   "NANDU" → "Ñandú Tecnología".
3. Limpiar la búsqueda. Marcar "Desarrollo a la medida" → 3 empresas.
   Marcar además "Fábrica de software" → 5 (cualquiera de las dos).
4. Con los chips puestos, escribir "so" → se combinan los dos filtros.
5. Escribir "zzz" → "Ninguna empresa coincide…" con *Limpiar filtros*.
6. *Nueva empresa* → *Guardar* vacío: errores en el nombre y la
   clasificación. Llenar un email inválido: error en el email.
7. Crear una con el nombre "consultores agiles s.a.s." → "Ya existe una
   empresa con este nombre".
8. Crear una válida → vuelve a la lista con "Empresa agregada".
9. Abrir una empresa, cambiar algo y darle Back → "¿Descartar los cambios?".
10. Rotar con el formulario a medio llenar y con el diálogo de eliminar
    abierto: sigue igual.
11. Matar el proceso con el formulario a medio llenar (app en segundo
    plano, `adb shell am kill co.edu.unal.softwaredirectory`) y volver desde
    Recientes: el borrador sigue ahí.
12. Íconos de web, teléfono y email: abren el navegador, el marcador y el
    correo.
13. *Eliminar* → *Cancelar*: sigue ahí. *Eliminar* → *Eliminar*: vuelve a la
    lista con "Empresa eliminada".

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.softwaredirectory` |
| Activity inicial | `ui.list.CompanyListActivity` |
| Versión | `versionCode 8`, `versionName "8.0"`; la app se llama "Directorio R8" |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding + ViewModel (MVVM) |
| Base de datos | SQLite con `SQLiteOpenHelper`, sin dependencias extra |
| Tests | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17 (solo para tests) |

## Decisiones

- **`SQLiteOpenHelper` en vez de Room**: el reto es sobre SQLite y el
  enunciado enlaza un tutorial de `SQLiteOpenHelper`. Así el esquema, las
  consultas, los `?` y las transacciones quedan a la vista. Room usaría
  SQLite por debajo, pero escondería el SQL detrás de anotaciones.
- **Tabla intermedia para la clasificación**, en vez de tres columnas
  booleanas o un entero con flags: es la forma relacional de guardar "una o
  varias". Además se lee bien en el Database Inspector, y agregar una
  clasificación nueva no cambia el esquema de `companies`.
- **Dos pantallas, sin submenús** (pedido del profesor): la lista y el
  formulario, que sirve para crear, ver y editar. El menú ⋮ solo tiene Tema
  y Acerca de.
- **Confirmación sin "deshacer"**: el diálogo ya es la protección que pide
  el enunciado. Un "deshacer" encima obligaría a reinsertar la empresa con
  sus clasificaciones desde otra pantalla.
- **Tests**: 54.
  - **SQLite de verdad con Robolectric** (SDK 37, SQLite nativo, base en
    memoria): 16 del repositorio y 3 del helper.
    - CRUD y transacciones.
    - Nombre repetido con o sin tildes.
    - CASCADE.
    - LIKE con `%` y `_`.
    - Valores con aspecto de SQL.
    - Filtro por clasificación sin recortar las demás.
    - Restricciones `CHECK` y FK.
    - Datos de ejemplo.
  - **ViewModels con el fake**: 16 del formulario y 8 de la lista.
    - Debounce de la búsqueda.
    - Filtro combinado.
    - Borrador y diálogo tras la muerte del proceso.
    - Doble toque al guardar.
    - Fallo al escribir.
  - **Kotlin puro**: 8 del validador y 3 de la normalización.
