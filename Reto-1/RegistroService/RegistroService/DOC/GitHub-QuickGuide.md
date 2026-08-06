# 🚀 GUÍA RÁPIDA: Cómo Subir a GitHub en 8 Bloques

## ⚡ TL;DR (Too Long; Didn't Read)

**8 commits = 8 bloques lógicos**

```
 ️1.  Setup              → git add *.sln *.csproj ... → feat: setup inicial
  2.  Modelo             → git add Domain/Entities Domain/Enums → feat: modelo
  3.  Excepciones        → git add Domain/Exceptions → feat: excepciones
  4.  Repositorio        → git add Domain/Repositories → feat: repositorio
  5.  Servicio           → git add Domain/Services → feat: servicio
  6.  DTOs               → git add API/DTOs API/Extensions → feat: DTOs
  7.  Endpoints          → git add Program.cs → feat: endpoints
  8.  Documentación      → git add DOC/ → docs: documentación
```

**Resultado:** Historial profesional en GitHub 

---

## 🎯 PASO A PASO

### PASO 1: Preparar Git Localmente

```bash
# Ir a carpeta del proyecto
cd c:\Users\DANIEL-PC\RiderProjects\proyecto-final-microservicios\Reto-1\RegistroService

# Inicializar repositorio
git init

# Configurar identidad (solo una vez)
git config user.name "Tu Nombre Completo"
git config user.email "tu.email@ejemplo.com"

# Añadir remoto (GitHub)
git remote add origin https://github.com/TU_USUARIO/proyecto-final-microservicios.git
```

### PASO 2: Crear .gitignore

```bash
# Crear archivo .gitignore
cat > .gitignore << 'EOF'
# Build results
bin/
obj/
*.dll
*.exe
*.pdb

# Visual Studio / Rider
.vs/
.idea/
*.user
*.userprefs
*.DotSettings
.vscode/

# Node.js
node_modules/

# Archivos temporales
*.tmp
*.log

# macOS
.DS_Store
EOF

# Commitear
git add .gitignore
git commit -m "chore: agregar .gitignore para proyecto .NET"
```


### PASO 3: Subir a GitHub

```bash
# Cambiar rama a 'main' (de 'master' si es necesario)
git branch -M main

# Push a GitHub
git push -u origin main

# (En Windows, te pedirá autenticación si no tienes SSH configurado)
```

### PASO 44: Verificar en GitHub

```
Abrir: https://github.com/TU_USUARIO/proyecto-final-microservicios

✓ Verás 8 commits
✓ Cada commit con su descripción
✓ Archivos organizados por carpeta
✓ Historial limpio
```

---

## 📊 Tabla de Bloques

| # | Tema | Tipo | Archivos | Comando |
|---|------|------|----------|---------|
| 1 | Setup | feat | 5 | `git add *.sln *.csproj ...` |
| 2 | Modelo | feat | 2 | `git add Domain/Entities Domain/Enums` |
| 3 | Excepciones | feat | 2 | `git add Domain/Exceptions/` |
| 4 | Repositorio | feat | 2 | `git add Domain/Repositories/` |
| 5 | Servicio | feat | 1 | `git add Domain/Services/` |
| 6 | DTOs | feat | 3 | `git add API/` |
| 7 | Endpoints | feat | 1 | `git add Program.cs` |
| 8 | Docs | docs | 4 | `git add DOC/` |

---

## ⚠️ Errores Comunes

### ❌ "fatal: not a git repository"
**Solución:** Ejecuta `git init` primero

### ❌ "error: The requested URL returned error: 403"
**Solución:** Revisa credenciales en GitHub → Settings → Developer settings → Personal access tokens

### ❌ "Commits muy grandes"
**Solución:** Divide más en bloques (aunque nuestra estrategia ya está bien dividida)

### ❌ "No se ve la documentación en GitHub"
**Solución:** Crea un `README.md` en la raíz y un segundo en `DOC/`

---

## ✅ Checklist de Subida

- [ x ] `git init` ejecutado
- [ x ] `.gitignore` creado
- [ x ] `git config user.name` y `user.email` configurados
- [ x ] `git remote add origin` conectado a GitHub
- [ x ] `git branch -M main` ejecutado
- [ x ] `git push -u origin main` ejecutado exitosamente
- [ x ] Repositorio visible en github.com
- [ x ] Documentación visible en GitHub

---


##  Soporte Rápido

| Problema | Comando |
|----------|---------|
| Ver commits | `git log --oneline` |
| Ver cambios | `git status` |
| Deshacer commit | `git reset HEAD~1` |
| Cambiar mensaje | `git commit --amend -m "nuevo mensaje"` |
| Push con force | `git push -u origin main --force` (⚠️ úsalo con cuidado) |

---

Ver archivos de documentación en `DOC/` para más detalles.
