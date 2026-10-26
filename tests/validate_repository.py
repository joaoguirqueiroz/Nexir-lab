"""Repository preflight; runs without Android SDK."""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

base = pathlib.Path(__file__).resolve().parents[1]
files = [
    '.github/workflows/android.yml', 'settings.gradle.kts', 'build.gradle.kts',
    'app/build.gradle.kts', 'app/src/main/AndroidManifest.xml',
    'app/src/main/java/br/com/vertice81/nexir/MainActivity.kt',
    'app/src/main/java/br/com/vertice81/nexir/IrCore.kt',
    'app/src/main/java/br/com/vertice81/nexir/BluetoothBridge.kt',
    'app/src/main/assets/profiles.json', 'firmware/esp32/NexirBridge.ino',
    'docs/INSTALACAO.md', 'README.md',
]
missing = [p for p in files if not (base / p).is_file()]
if missing:
    sys.exit('Arquivos ausentes: ' + ', '.join(missing))
manifest = ET.parse(base / 'app/src/main/AndroidManifest.xml').getroot()
assert manifest.tag == 'manifest'
build = (base / 'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'minSdk = 26' in build
assert 'compileSdk = 35' in build
assert 'implementation("com.google.mlkit:text-recognition:16.0.1")' in build
workflow = (base / '.github/workflows/android.yml').read_text(encoding='utf-8')
assert 'gradle-version:' in workflow and 'assembleDebug' in workflow
assert 'gh release' in workflow
assert 'NEXIR-LAB.apk' in workflow
print(f'OK: {len(files)} arquivos críticos, AndroidManifest e configuração CI')
