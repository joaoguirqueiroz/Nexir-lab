# NEXIR LAB 1.2 — controle direto do celular (sem ESP32)

## O que funciona nesta versão

**IR nativo:** se `ConsumerIrManager.hasIrEmitter()` for verdadeiro, o aplicativo transmite códigos IR da biblioteca local. Se o modelo da TV não corresponder aos códigos, use outro perfil ou cadastre comandos compatíveis. A API Android **não recebe IR**.

**Roku na rede:** descoberta voluntária por SSDP (serviço `roku:ecp`) e identificação por `GET /query/device-info`. O app mostra nome, modelo, versão do software e IP. Após escolher o aparelho, comandos de controle são enviados via Roku ECP, por HTTP local na porta 8060, somente para IPs privados. Nenhum comando é enviado durante a descoberta.

**mDNS:** encontra anúncios `_googlecast._tcp.`, `_airplay._tcp.` e `_http._tcp.`, mas **não** ativa automaticamente um controle. O nome anunciado na rede não constitui prova de marca, modelo ou autorização.

**Sem Wi-Fi:** apenas IR, caso o smartphone tenha hardware IR. **Sem IR e sem protocolo de rede compatível:** não existe acesso universal. Um módulo receptor/transmissor externo é opcional, não obrigatório para IR nativo ou Roku.

## Como usar

1. Abra **Dispositivos** e toque em **Buscar dispositivos** com o celular no mesmo Wi-Fi.
2. Se a TV Roku aparecer, leia o modelo/firmware e escolha **Usar como controle de rede**.
3. Abra **Controle**, confirme a autorização e envie comandos individuais.
4. Se não aparecer, selecione **Adicionar Roku por IP** e informe o IP privado do dispositivo.
5. Para controle IR tradicional: selecione **INFRAVERMELHO**, escolha um perfil e aponte para o receptor.
6. Para identificar modelos não anunciados pela rede: **Laboratório > Abrir leitura de etiqueta**.

### Requisitos Roku

No Roku, o recurso **Control by mobile apps / Controle por aplicativos móveis** precisa estar habilitado, segundo as configurações do próprio fabricante. Alguns modelos não suportam HDMI, energia ou volume pela rede; nesses casos o comando pode falhar ou não produzir efeito. Nunca use o modo *Permissive* desnecessariamente.

Documentação oficial: https://developer.roku.com/dev/docs/external-control-api

## Segurança e privacidade

- Procura na **rede Wi-Fi selecionada pelo Android**, por iniciativa do usuário; não há exploração, quebra de autenticação ou varredura indiscriminada de portas.
- IP informado manualmente é limitado a intervalos privados `10/8`, `172.16/12` e `192.168/16`.
- O protocolo ECP histórico utiliza HTTP, por isso o app habilita cleartext em nível de aplicação. O código restringe as chamadas de controle ao **IP privado e porta 8060**. Evite redes Wi-Fi abertas/desconhecidas.
- No Android 17 (target SDK 37+), será necessário adaptar o fluxo ao novo acesso à rede local (`ACCESS_LOCAL_NETWORK`). A compilação atual usa target SDK 34.
- Reconhecimento de etiqueta via ML Kit embarcado funciona offline.
- Não há garantia de controle de telões LED profissionais, TVs Samsung/LG ou Chromecast sem suporte de protocolo e autorização específicos.
- Para atualizações APK sobre versões instaladas, configure assinatura de produção persistente. O release automático atual é **debug/teste**.

## Testes recomendados

- Celular **com IR** + TV de laboratório com perfil IR correto.
- Celular **sem IR** + TV Roku no mesmo Wi-Fi, com controle móvel habilitado.
- Celular **sem IR** + Chromecast: aparece na descoberta se anunciar mDNS; o controle específico não deve ser habilitado.
- Roku com controle móvel desabilitado: HTTP 403 tratado com mensagem ao usuário.
- Wi-Fi desconectado: alerta ao tentar busca.
- Endereço IPv4 público no campo manual: bloqueado.
- Telão de fabricante não suportado: mostrar identificação parcial e pedir fluxo oficial de pareamento, sem alegar controle.
