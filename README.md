# W-mode

Checklist personal de hábitos y tareas para Android, con historial, programación semanal, notificaciones y seguimiento de cumplimiento.

## Funciones

- Checklist editable.
- Una tarea completada queda bloqueada hasta el próximo día en que corresponda.
- El día operativo cambia a las 00:01.
- Tareas diarias o programadas para uno o varios días de la semana.
- Notificaciones configurables por tarea, con horario y tono.
- Historial semanal, mensual y anual.
- Estadísticas de cumplimiento, días perfectos y racha.
- Gráfico lineal animado de cumplimiento.
- Confetti al completar el día.
- Navegación por swipe entre Hoy, Historial y Config.
- Exportación e importación de backup JSON desde Config > Datos.
- Datos guardados localmente en SQLite.
- Funciona offline.

## Backup

Desde **Config > Datos** se puede:

- Exportar una copia de seguridad `W-mode-backup-AAAA-MM-DD.json`.
- Importar una copia de seguridad creada por W-mode.

El backup incluye tareas, orden, días programados, configuración de avisos y todo el historial de cumplimiento. Los permisos del sistema Android no forman parte del backup.

## Build release y firma

El workflow `Build W-mode Release` compila `assembleRelease`.

Para que GitHub Actions firme automáticamente la APK con la clave permanente de W-mode, el repositorio debe tener configurados estos Secrets:

- `MODOW_KEYSTORE_BASE64`
- `MODOW_KEYSTORE_PASSWORD`
- `MODOW_KEY_ALIAS`
- `MODOW_KEY_PASSWORD`

Los valores de esos Secrets **no deben guardarse en el repositorio**.

Cuando los cuatro Secrets están disponibles y la build corre sobre `main`, el workflow verifica la firma y crea/actualiza automáticamente la Release correspondiente.

## Versionado

- `v1.5.0`: versión funcional con gráfico, check animado, borrado de tareas y múltiples días.
- `v1.5.1`: versión estable con backup/importación y pipeline de release preparado.
