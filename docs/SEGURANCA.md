# Escopo de segurança

O aplicativo é dedicado ao controle de equipamentos próprios ou expressamente autorizados e à pesquisa de protocolos IR. A interface não dispõe de modo para transmissão contínua, varredura exaustiva de códigos, bloqueio de receptores ou ataques de indisponibilidade. A transmissão física depende de linha de visão, receptor e perfil compatível.

O Android não permite capturar sinais IR pela API pública `ConsumerIrManager` apenas com emissor integrado. A captura implementada usa sensor IR externo conectado ao ESP32. O reconhecimento de etiqueta por OCR extrai texto da imagem; não detecta remotamente o modelo via infravermelho.

Não coloque senhas, chaves de assinatura ou tokens GitHub no repositório. Um APK de desenvolvimento pode ser instalado para teste, mas não use assinatura debug para distribuição pública definitiva.
