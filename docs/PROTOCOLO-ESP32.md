# Protocolo Bluetooth da ponte ESP32

Transporte: Bluetooth Classic Serial Port Profile (SPP), UUID 00001101-0000-1000-8000-00805F9B34FB, UTF-8/ASCII, uma mensagem por linha (`\n`). O telefone precisa parear o ESP32 nas configurações do sistema.

## App -> ESP32

- `PING` -> `OK|PONG`
- `CAPTURE` -> `OK|CAPTURE_ARMED`. Próximo sinal do controle original gera `{"type":"ir","protocol":"RAW","frequency":38000,"pattern":[9000,4500,...]}` + `OK|CAPTURE_DONE|CARRIER_ASSUMED_38KHZ`.
- `TX|38000|9000,4500,560,560,...` -> `OK|TX|4|14620`, em caso de transmissão solicitada ao driver. **Não prova recepção pela TV.**
- `CANCEL` -> `OK|CAPTURE_CANCELLED`.
- `ERR|...` para erros de validação.

`frequency` em Hz é frequência **configurada**. Sensor receptor IR demodulador 38 kHz não mede portadora; o valor 38000 enviado na captura é o presumido do módulo. Timing em microssegundos; ordem ON, OFF, ON, OFF. Primeiro item de `rawbuf` do IRremoteESP8266 é o gap inicial e é descartado. Mensagens máximas de 3900 caracteres, 500 durações, menos de 2 s total.

## Notas elétricas

- ESP32 original com suporte a Bluetooth Classic SPP; não funciona com ESP32-C3/S3 (BLE-only).
- Receptor VS1838B ou TSOP38238 alimentado em 3,3 V; conferir pinagem específica.
- Para o LED emissor usar transistor/resistor, não ligar LEDs de alta corrente diretamente no pino.
- Não conectar componentes a circuitos de TVs; o projeto opera de forma óptica sem modificar eletrônicos alheios.
