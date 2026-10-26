# Diagnóstico técnico

| Problema | Causa provável | Verificação |
| --- | --- | --- |
| Build falha no Gradle | Dependência transitória/repositório fora do ar ou código não compilando | Abrir log no Actions; etapa `Testes Kotlin e compilacao do APK` |
| Release não criado | Permissões do repositório impedindo `contents: write` | `Settings > Actions > General` |
| `NEXIR-LAB.apk` ausente | Build não finalizado | Verificar `:app:assembleDebug` e `app/build/outputs/apk/debug/` |
| Não instala por cima | Assinaturas debug diferentes | Exportar biblioteca, desinstalar versão antiga e instalar a nova |
| Android sem IR | Aparelho não possui emissor acessível | `DIAGNÓSTICO > IR NATIVO AUSENTE` |
| Captura não funciona | Celular só possui emissor IR | Usar receptor IR externo/ESP32 |
| TV não responde | Código, marca, modelo ou protocolo incompatível | Usar aprendizado IR com controle original |
| ESP32 não conecta | Bluetooth Classic SPP indisponível ou não pareado | Confirmar ESP32 original, parear `NEXIR-BRIDGE` e permitir acesso Bluetooth |
| Leitura de etiqueta falha | Foto pouco legível | Usar luz ambiente, enquadrar texto e tentar novamente |

Não há confirmação de que os comandos chegaram à TV apenas pelo retorno da API de transmissão.
