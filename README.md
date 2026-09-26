# Códigos USSD Cuba (ETECSA)

App Android completa con los códigos USSD más usados de ETECSA en Cuba.

## Características

- Lista completa de códigos USSD actualizados (consulta de saldo, planes, recargas, desvíos, emergencias, etc.)
- Toca cualquier código para marcarlo directamente
- Interfaz moderna Material Design 3
- Colores inspirados en la bandera de Cuba

## Códigos incluidos

| Código | Descripción |
|--------|-------------|
| `*222#` | Saldo principal + recursos |
| `*222*328#` | Plan de datos |
| `*222*266#` | Bonos y planes USD |
| `*222*869#` | Plan de voz |
| `*222*767#` | Plan de SMS |
| `*222*732#` | Estado recargas nacionales |
| `*133#` | Comprar planes |
| `*234#` | Transferir / Adelanta saldo |
| `*666` | Recargar con tarjeta |
| Y muchos más...

## Descargar APK

1. Ve a la pestaña **Actions** de este repositorio
2. Selecciona el último workflow **Build APK**
3. Descarga el artifact **ussd-cuba-apk**

O espera a que se genere automáticamente tras cada push a `main`.

## Compilar localmente

```bash
./gradlew assembleDebug
```

El APK quedará en `app/build/outputs/apk/debug/`.

## Licencia

Uso libre. Los códigos USSD pertenecen a ETECSA.
