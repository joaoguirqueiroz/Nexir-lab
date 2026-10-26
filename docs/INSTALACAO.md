# Como instalar o NEXIR LAB no Android

## Baixar o APK pelo GitHub

1. Abra o repositório GitHub do NEXIR LAB.
2. Entre em **Releases**, na lateral direita do repositório (ou na aba Releases).
3. Abra **NEXIR LAB — APK automático (teste)**, identificado pela versão `continuous`.
4. Expanda **Assets** e selecione **NEXIR-LAB.apk**.
5. No celular, abra o APK e conceda ao navegador/gerenciador de arquivos a permissão **Instalar apps desconhecidos** se o Android solicitar.
6. Confirme **Instalar**. Quando terminar, procure **NEXIR LAB** na lista de aplicativos.

**Download alternativo:** abra **Actions > Android — Compilar e publicar APK > execução verde > Artifacts > NEXIR-LAB-Android-APK**. O GitHub baixa um ZIP; extraia e instale o APK contido nele. Pode exigir login na conta GitHub.

## Primeira utilização

1. Abra o app e selecione **BIBLIOTECA**.
2. Escolha um perfil compatível com o aparelho de sua propriedade ou crie um novo.
3. Em **DIAGNÓSTICO**, confira **EMISSOR IR DISPONÍVEL**. Se aparecer **IR NATIVO AUSENTE**, será necessário ESP32 com LED IR ou outro celular com emissor.
4. Em **CONTROLE**, escolha **IR DO CELULAR** ou **ESP32 EXTERNO**.
5. Aponte o emissor para o receptor do equipamento e pressione um comando; confirme visualmente se houve resposta.
6. Para copiar comandos de controle físico, abra **APRENDER** usando um ESP32 com receptor IR conectado.

## Câmera e leitura de etiqueta

A aba **IDENTIFICAR** extrai o texto impresso na etiqueta usando OCR offline, sugere perfis e pede confirmação. Não identifica automaticamente qualquer modelo por transmissão IR.

## Atualização do APK

Esta entrega automatiza um **APK de desenvolvimento assinado com chave debug temporária**, não um pacote de produção. Execuções diferentes podem ter assinaturas diferentes e uma instalação por cima da versão anterior pode falhar. Antes de desinstalar: **BIBLIOTECA > EXPORTAR BIBLIOTECA**, salve seu JSON; depois desinstale, instale o APK novo e importe o JSON.

Para atualizações contínuas sem desinstalar, configure assinatura de produção usando uma chave privada persistente e nunca a publique no repositório. Consulte `docs/GITHUB-ACTIONS.md`.

## Resolução de problemas

- “App não instalado”: verifique memória, Android 8.0+, assinatura divergente da instalação anterior.
- Sem resposta da TV: confira receptor, distância, perfil, padrão IR, bateria/estado do equipamento.
- Sem emissor IR: use ESP32 com transmissor IR ou outro celular compatível.
- Sem captura no celular: a API Android `ConsumerIrManager` **não capta** sinais IR. Use receptor externo.
- ESP32 não aparece: emparelhe nas configurações do Bluetooth primeiro; use ESP32 original com Bluetooth Classic SPP.
- OCR não localiza modelo: ajuste luz/foco da etiqueta; crie um perfil manual.
