# Icone do app

O desenho vive em `src/jvmMain/kotlin/com/example/sonntag/ui/icon/AppIcon.kt` — e a
mesma funcao usada para o icone da janela em execucao, que destaca os dias de reuniao
configurados. Os arquivos aqui sao a versao estatica usada pelos instaladores, com o
padrao `DEFAULT_MEETING_DAYS` (quinta e domingo), ja que um instalador nao conhece a
configuracao do usuario.

| Arquivo         | Uso                     |
| --------------- | ----------------------- |
| `app-icon.png`  | Linux (deb) + mestre    |
| `app-icon.ico`  | Windows (msi/exe)       |
| `app-icon.icns` | macOS (dmg)             |

## Como regerar

```bash
# 1. PNG mestre a partir do codigo Kotlin
./gradlew :composeApp:exportAppIcon

# 2. .ico e .icns derivados do PNG (requer Pillow)
python3 -c "
from PIL import Image
m = Image.open('composeApp/icons/app-icon.png')
m.save('composeApp/icons/app-icon.ico', sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
m.save('composeApp/icons/app-icon.icns')
"
```
