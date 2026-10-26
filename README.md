# NEXIR LAB — Infrared Control & Analysis

## Download e instalacao pelo GitHub

- **[GitHub Actions — acompanhar compilacao](https://github.com/joaoguirqueiroz/Nexir-lab/actions)**
- **[Releases — baixar APK apos build verde](https://github.com/joaoguirqueiroz/Nexir-lab/releases)**
- **[Guia de instalacao](docs/INSTALACAO.md)** e **[Solucao de problemas](docs/RESOLUCAO-DE-PROBLEMAS.md)**

Sempre que houver um novo envio a branch `main`, o workflow `.github/workflows/android.yml` executa validacoes, testes, compilacao Android e, se todas as etapas passarem, publica `NEXIR-LAB.apk` no Release `continuous`. O APK tambem fica como artefato temporario em Actions. Se nao houver Release ou o workflow estiver vermelho, ainda nao existe APK validado para esta revisao.

> **Atualizacoes:** o APK automatico atual e assinado em modo debug. A chave do runner pode mudar entre compilacoes; Android pode recusar instalar a nova versao por cima da antiga. Para atualizacoes sem desinstalar, use assinatura permanente com keystore de producao configurado de forma privada. Nao publique a chave no repositorio.


Aplicativo Android **nativo** de controle remoto infravermelho e laboratório de sinais IR para equipamentos próprios/autorizados. Interface escura em português. Código Kotlin, Android Studio, perfis locais JSON, captura externa com ESP32 Bluetooth Classic SPP e leitura assistida de etiquetas via OCR ML Kit **embarcado** (offline).

## O que foi implementado

- **Controle avançado real:** Power, volume, mute, canais, HDMI/fonte, menu, back, home, setas, OK, controles de mídia e comandos especiais cadastráveis. Comandos ausentes são mostrados desativados visualmente, e podem ser cadastrados/aprendidos.
- **IR nativo (somente transmissão):** `ConsumerIrManager.hasIrEmitter`, `getCarrierFrequencies` e `transmit`. Verifica formato, frequência, total de duração e limites antes do envio.
- **ESP32:** conexão com dispositivo **já pareado**; solicita captura do controle original, valida sinal recebido e salva como botão RAW; pode retransmitir pelo LED IR do ESP32 quando celular não possui emissor.
- **Biblioteca offline:** três perfis iniciais de referência (Samsung, LG, controle de bancada NEC), novo perfil, seleção, salvar/alterar códigos e exportar/importar JSON. Os códigos são **referenciais, não garantem compatibilidade** para cada aparelho.
- **Identificação por foto:** câmera/galeria; reconhecimento OCR de etiqueta e sugestão de perfil com **confirmação explícita**. Marca e modelo não podem ser lidos via IR de transmissão.
- **Auditoria IR supervisionada:** escolha manual de um comando de perfil, confirmação explícita de envio único, marcação manual de execução/não execução, histórico e compartilhamento do relatório. Não há interferência automática, varreduras ou comandos em massa.
- **Transparência de captura nativa:** informa que `ConsumerIrManager` oferece apenas envio e que aprender sinais requer receptor externo, salvo hardware proprietário com SDK de recepção específico.
- **Diagnóstico:** faixa de portadoras suportadas, frequência solicitada, quantidade de durações, duração total do sinal, caminho de transmissão, status Bluetooth e histórico de eventos.
- **Segurança:** sem permissão de internet; confirmação de uso em aparelhos autorizados; nada de comandos enviados automaticamente, força bruta ou interferência deliberada com telas alheias.

## APK automático ao enviar ao GitHub

**O repositório já vem configurado para gerar um APK instalável em cada atualização da branch `main`.**

1. Descompacte o arquivo `NEXIR-LAB-GITHUB-READY.zip`.
2. Crie um repositório GitHub vazio e envie os arquivos extraídos, inclusive `.github/workflows/android.yml`.
3. Confira **GitHub > Actions > Android — Compilar e publicar APK**.
4. Quando a compilação concluir em verde, abra **Releases > NEXIR LAB — APK automático (teste) > Assets > NEXIR-LAB.apk** para instalar no Android.

Para publicar pelo Windows com Git instalado, execute em PowerShell dentro da pasta descompactada:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\enviar-para-github.ps1 -RepositoryUrl "https://github.com/SEU_USUARIO/NEXIR-LAB.git"
```

**Importante:** por enquanto é um APK `debug`, instalável para testes. Para atualizar sem desinstalar a versão anterior de diferentes execuções do CI, configure assinatura persistente de produção. Consulte [`docs/GITHUB-ACTIONS.md`](docs/GITHUB-ACTIONS.md) e [`docs/INSTALACAO.md`](docs/INSTALACAO.md).

**Arquivos úteis:** [guia GitHub Actions](docs/GITHUB-ACTIONS.md) · [como instalar](docs/INSTALACAO.md) · [solução de problemas](docs/RESOLUCAO-DE-PROBLEMAS.md) · [segurança](docs/SEGURANCA.md).

## Como compilar

**Requisitos:** Android Studio atualizado, Android SDK API 35, JDK 17, acesso inicial à internet para Gradle e ML Kit. Android 8.0+.

1. Descompacte `NEXIR-Lab.zip` e abra **a pasta `NEXIR-Lab`** no Android Studio.
2. Aguarde o Gradle Sync e instale as versões de SDK solicitadas.
3. Se o Android Studio pedir Gradle, selecione Gradle 8.9 em *Settings > Build Tools > Gradle* (o projeto não distribui um wrapper JAR binário).
4. Clique em **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. O APK de desenvolvimento estará em `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions — APK automático

Envie os arquivos à branch `main`: o workflow **Android — Compilar e publicar APK** inicia automaticamente, publica o APK nos artefatos e, se autorizado, no Release `continuous`. Configura JDK 17, Android SDK 35 e Gradle 8.9. APK de desenvolvimento (`debug`), não release de produção assinado permanentemente.

> Limitação da entrega: este ambiente não tem Android SDK nem Gradle e não tem acesso às distribuições externas; por isso não há APK compilado/testado nesta entrega. Foram incluídos testes de lógica, validações de JSON e configuração CI; hardware IR/ESP32 precisa de teste físico.

## Hardware

### Somente celular com emissor IR

Não exige hardware adicional. O aparelho precisa expor o serviço Consumer IR do Android. Se mostrar `IR NATIVO AUSENTE`, use a ponte externa abaixo.

### Capturar/aprender IR ou transmitir com celular sem IR

- ESP32 DevKit V1 com chip **ESP32 original/WROOM** (Classic Bluetooth, não C3/S3).
- Receptor IR 38 kHz (ex.: TSOP38238 / VS1838B) no **GPIO 15**, VCC 3V3, GND.
- LED infravermelho no **GPIO 4** via transistor/resistor apropriado, GND comum. Consulte ficha do LED e módulo.
- Arduino IDE: instale **ESP32 board support** e biblioteca **IRremoteESP8266**, abra `firmware/esp32/NexirBridge.ino` e envie o sketch à placa.
- No Android, emparelhe `NEXIR-BRIDGE` em Configurações > Bluetooth.
- No NEXIR LAB, abra **APRENDER**, conecte o ESP32 pareado, pressione **AGUARDAR PRÓXIMO SINAL IR**, aponte o controle original ao sensor e pressione um botão. Dê nome ao comando e salve no perfil atual.
- Para transmitir via ESP32, vá ao **CONTROLE** e selecione **ESP32 EXTERNO**.

## Identificação por etiqueta

**IDENTIFICAR > FOTOGRAFAR ETIQUETA** ou selecione uma imagem da galeria. O ML Kit com modelo latino embarcado extrai o texto localmente. O projeto não faz reconhecimento visual geral de logotipos: extrai texto de nomes/marcas e sugere perfis em sua biblioteca. Não há inferência de modelo via IR de mão única. Se não houver perfil correspondente, adicione um perfil e aprenda comandos com ESP32.

## Captura usando apenas o celular — o que é possível

O emissor IR embutido nos celulares Android normalmente é um **transmissor**, não um receptor acessível. A API pública `ConsumerIrManager` expõe `hasIrEmitter`, `getCarrierFrequencies` e `transmit`, mas nenhuma operação de recepção/captura. Portanto, a aba APRENDER não promete captura nativa só porque o telefone tem IR. Se um fabricante disponibilizar API proprietária e receptor físico, o suporte exigirá implementação por modelo e testes com hardware.

**Fluxo funcional disponível:** capturar no ESP32 com sensor IR -> salvar como comando RAW -> selecionar saída **IR do celular** para retransmitir usando hardware nativo, ou saída **ESP32** para retransmitir externamente. Na aba **AUDITORIA**, o usuário escolhe um comando, envia uma vez para equipamento autorizado e registra manualmente a resposta observada.

## Compatibilidade e limitações

- O app não detecta automaticamente dispositivos por infravermelho; um emissor IR somente transmite, não identifica marca/modelo ou confirma aceitação.
- Bibliotecas comerciais de comandos variam por fabricante/ano. O resultado real depende da versão do controle/receptor, linha de visada e hardware.
- Protocolos codificados diretamente: **NEC (32), Samsung (32), Sony SIRC12** e padrões **RAW** capturados/importados.
- O suporte a Sony SIRC existe no motor de codificação, mas não há perfis Sony pré-carregados; comandos e repetição variam por equipamento.
- Algumas funções de controle de TV não têm códigos padronizados e exigem aprendizado do controle original.
- A ponte ESP32 confirma que recebeu/mandou o sinal, não a ação na TV.
- O receptor IR 38 kHz não mede a portadora; o diagnóstico mostra frequência configurada/presumida, não medida.

## Formato do banco local

`app/src/main/assets/profiles.json` é uma lista JSON:

```json
[
  {
    "id": "meu-projetor",
    "name": "Projetor laboratório",
    "brand": "Exemplo",
    "model": "XXXX",
    "note": "Comandos aprendidos",
    "commands": {
      "POWER": {"protocol":"NEC","order":"MSB","frequency":38000,"code":"20DF10EF"},
      "HDMI2": {"protocol":"RAW","frequency":38000,"pattern":[9000,4500,560,560]}
    }
  }
]
```

Use perfis importados apenas de fontes confiáveis. Os arquivos são validados quanto a quantidade, duração, frequência e sintaxe, mas importar configurações incorretas pode gerar comandos inválidos. `HOME`, `SOURCE`, `PLAY_PAUSE`, `NEXT`, `PREVIOUS` podem ser adicionados sob medida.

## Testes e publicação

Workflow: `.github/workflows/android.yml`. Testes Kotlin: `app/src/test/java/br/com/vertice81/nexir/IrEngineTest.kt`. Para executar localmente com Gradle instalado: `gradle testDebugUnitTest :app:assembleDebug`.

Licença MIT — veja `LICENSE`.
