# Corrección de las demos locales — 20 de septiembre de 2026

## Hallazgos

- Producción conservaba OpenAI habilitado y usaba el puente FastAPI privado, mientras la copia de desarrollo estaba migrando al gateway RAG. El cliente admite ambos contratos; el despliegue actual usa FastAPI/Ollama.
- El chat enviaba un prompt largo en inglés, con perfil duplicado, a una ventana de 2048 tokens y no enviaba historial. Se compactaron las instrucciones en español y se añadió historial limitado por sesión con contexto de 4096 tokens.
- El cliente SSE descartaba fragmentos formados solo por espacios. Ahora conserva todos los fragmentos no vacíos.
- La demo de turnos usaba reglas sin consultar Qwen. Perdía datos en búsquedas sin fecha/hora exactas y rechazaba nombres aislados al iniciar. Las sesiones se agrupaban por IP.
- El resumen PDF dependía exclusivamente de OpenAI. Ahora extrae texto con PDFBox y resume mediante Qwen, sin conservar el documento.

## Comportamiento nuevo

OpenAI queda bloqueado en el backend incluso si existe una clave antigua. La interfaz ofrece únicamente Qwen. Los turnos conservan nombre, fecha y hora entre mensajes, aceptan correcciones, horarios aislados y selección de alternativas; permiten corregir el nombre de una reserva y reprogramarla conservando los demás datos. Las confirmaciones negativas no reservan. Las sesiones usan un identificador independiente de la IP. Hay entrada por texto además de voz.

Qwen ayuda a extraer datos de frases que las reglas no reconocen; el backend valida la disponibilidad y ejecuta las operaciones. Las respuestas que confirman reservas se construyen desde el resultado de la base de datos. El estado pendiente vive 2 horas en memoria; un reinicio lo descarta. Las reservas permanecen en PostgreSQL.

## Modelos evaluados en la VPS

Equipo: 2 CPU, 3.8 GiB de RAM, sin swap, varios servicios compartidos. Pruebas con temperatura 0–0.15, sin razonamiento, contexto 2048/4096 y modelos descargados desde Ollama.

| Modelo | Archivo | Perfil/saludo, segundos por consulta con carga del modelo | Observación |
|---|---:|---:|---|
| qwen3:0.6b | 522 MB | 4.92–7.24 | Con instrucciones compactas contestó el perfil en español; el más rápido. Confundió primera/tercera persona en una pregunta de Python; se reforzó esa instrucción. |
| qwen2.5:1.5b-instruct | 986 MB | 8.89–12.59 | No mostró mejora consistente. Omitió nombre y hora en extracción de turnos. |
| qwen3.5:0.8b | 1.0 GB | 7.73–11.16 | Confundió al asistente con el titular e inventó hechos en un resumen de prueba. |

Se mantiene **qwen3:0.6b**, con el prompt y contexto corregidos. Esta muestra es una comparación operativa pequeña, no un benchmark general. Ninguno resultó suficientemente fiable para delegar la persistencia de turnos al modelo. Un modelo mayor requiere evaluar recursos y calidad en otra capacidad de VPS; no se activó uno que comprometiera los otros sitios.

Se amplió la evaluación con `qwen3:1.7b` en un contenedor aislado limitado a 1600 MiB: el proceso del modelo fue terminado al cargarlo por exceder ese presupuesto. Qwen2.5 con contexto 3072 pudo conversar, pero no recuperó correctamente el nombre del visitante e invirtió un dato del perfil. Qwen3 de 0.6B también puede repetir respuestas en conversaciones abiertas; el historial y los prompts mejoran el transporte del contexto, pero no eliminan las limitaciones del modelo. No se promete equivalencia con GPT.

Referencias: https://ollama.com/library/qwen3, https://ollama.com/library/qwen2.5:1.5b-instruct, https://ollama.com/library/qwen3.5.

## Verificación

La suite incluye conversaciones contra H2: datos separados, corrección de nombre previo y posterior a reserva, reprogramación con solo nueva hora o fecha, horario ocupado, selección de alternativa, confirmación negativa, fecha inválida y aislamiento entre sesiones. PDF se prueba con un archivo real generado en memoria y un cliente Qwen simulado. La compilación del frontend usa la base `/portfolio/`.

El respaldo de producción conserva código anterior e imágenes Docker etiquetadas antes del despliegue. Los resultados detallados de comparación de modelos quedan junto al respaldo en la VPS.

## Regresión reportada durante la validación

La transcripción «muevas ese turno de las 9 ... para las 12:30» descubrió que el patrón genérico `para <nombre>` aceptaba «las» como paciente. Además, confirmar priorizaba ese nombre pendiente sobre el horario, y los infinitivos «guardar»/«guardarlo» no contaban como confirmación. Se unificó la validación del nombre, se reconocen las variantes de «mover», se selecciona el horario de destino y la actualización de nombre requiere un nombre explícito en el mensaje actual. Corregir el nombre conserva un cambio de horario pendiente.

La transcripción completa se incorporó a las pruebas, junto con dos horarios en una frase, confirmación en infinitivo y corrección de nombre antes de confirmar otro horario. Suite final: 48 pruebas correctas. También se corrigió «martes 29» para respetar el número indicado en vez de elegir el martes más cercano. Se añadió revalidación HTTP del frontend para evitar mantener la interfaz antigua después de un despliegue.

Validación pública posterior al despliegue: conversación completa con martes 29 de septiembre, reserva a las 09:00, cambio a 12:30 conservando Juancito, corrección a Sebastián y reprogramaciones con «guardarlo» y «guardar». La agenda devolvió una sola reserva, a las 12:30 y con el nombre correcto. Las reservas creadas para QA se archivaron y eliminaron por su identificador de sesión. Health confirmó OpenAI desactivado y los tres endpoints de voz OpenAI devolvieron 503.
