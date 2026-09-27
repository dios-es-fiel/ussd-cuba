# Códigos USSD Cuba (ETECSA) v1.1

App Android con **50+ códigos USSD** de ETECSA, Transfermóvil y emergencias.

## Novedades v1.1
- Más códigos (inspirados en UtilEs / QvaCall)
- **Búsqueda** en tiempo real
- **Categorías** con chips: Consultas, Planes, Recargas, Llamadas, Internacional, Transfermóvil, Emergencias...
- Diseño mejorado (cards, badges de categoría)
- Códigos de Transfermóvil por USSD (para teléfonos sin app)

## Descargar APK

1. Ve a **Actions** → último workflow exitoso  
   https://github.com/luiseilerys/ussd-cuba/actions
2. Descarga el artifact **ussd-cuba-apk**
3. Descomprime e instala el `.apk`

## Códigos principales

| Código | Uso |
|--------|-----|
| `*222#` | Saldo + recursos |
| `*222*328#` | Datos |
| `*222*266#` | Bonos / USD |
| `*222*869#` | Voz |
| `*222*767#` | SMS |
| `*222*732#` | Límite recargas nacionales |
| `*133#` | Comprar planes |
| `*234#` | Transferir / Adelanta saldo |
| `*666` | Recargar tarjeta |
| `*99` | Cobro revertido |
| `*#06#` | IMEI |
| `*444*46#` | Saldo Transfermóvil |

## Compilar

```bash
gradle wrapper --gradle-version 8.2
./gradlew assembleDebug
```
