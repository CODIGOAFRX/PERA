# Inventario de DimproCristalWin

Genera `docs/26-inventario-dimprocristalwin.md` leyendo el código fuente del programa anterior.
Se ejecuta desde esta carpeta, con la ruta de la instalación de DimproCristalWin:

```
python menu_tree.py "C:/DimproCristalWin/MENUS/PRINCIPAL.MPR" menu_principal.json
python inventory_scan.py "C:/DimproCristalWin"
python inventory_doc.py
```

El estado de cada opción en PERA está en el diccionario `STATUS` de `inventory_doc.py`: se actualiza ahí al
terminar cada entrega y se vuelve a generar el documento. Los `.json` intermedios no se suben.
