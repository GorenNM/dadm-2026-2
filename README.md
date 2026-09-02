# DADM 2026-2

Repo del curso Desarrollo de Aplicaciones para Dispositivos Móviles (UNAL,
prof. Jorge E. Camargo). Repo único, público, dos ramas: `dev` (trabajo
diario) y `main` (protegida, solo vía PR).

## Estructura

```
dadm-2026-2/
├── talleres/reto-NN-slug/    # talleres individuales
└── proyecto/reto-NN-slug/    # entregables del proyecto en grupo
```

Cada carpeta de reto trae `enunciado.pdf`, proyecto Android, y `README.md`
propio explicando qué se hizo y cómo correrlo.

## Flujo de trabajo

- Desarrollo iterativo en `dev`, commits normales.
- Publicación a `main`: rama nueva desde `origin/main`, un solo commit
  limpio con solo la carpeta del reto correspondiente, PR contra `main`.
- **Nunca** commitear ni pushear directo a `main`.
