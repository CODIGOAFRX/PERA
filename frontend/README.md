# Frontend de PERA ERP

Cliente React + TypeScript + Vite del MVP horizontal. Consume exclusivamente `/api` para funcionar tanto con el proxy de Vite como con Nginx dentro de Docker.

## Desarrollo

Desde la raíz del repositorio, la opción más sencilla en Windows es:

```powershell
.\scripts\start-local.ps1
```

También se puede arrancar solo el frontend si el gateway ya escucha en `localhost:8080`:

```powershell
cd frontend
npm ci
npm run dev
```

La aplicación estará en `http://localhost:5173`.

## Comprobaciones

```powershell
npm test
npm run build
npm audit --omit=dev
```

Las credenciales iniciales del entorno de desarrollo son `admin` / `ChangeMe123!`.
