# APK Android para instalação direta

Baixe **NEXIR-LAB.apk** em **Assets**, abra no Android e autorize a instalação de apps dessa origem quando solicitado.

- **Tipo de compilação:** `debug`, assinada automaticamente pelo Android Gradle Plugin durante o build.
- **Atualizações:** novos builds de CI podem usar chaves debug diferentes. Se receber “Aplicativo não instalado”, faça backup da biblioteca pelo app, desinstale a versão anterior e instale a nova; isso apaga dados locais do app caso não exportados.
- **Versão mínima:** Android 8.0 (API 26).
- **IR:** transmissão nativa somente em celulares com emissor IR; aprendizado de sinais requer receptor externo compatível, como ESP32 com receptor 38 kHz.
- **Compatibilidade:** os perfis são exemplos de referência; nem todos os modelos aceitam todos os comandos.

O projeto é experimental e **não foi validado em todos os modelos de TVs, projetores ou celulares**. Veja [README](../../README.md) e `COMO-INSTALAR.md`.
