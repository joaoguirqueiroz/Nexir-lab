# Publicar APK automaticamente pelo GitHub Actions

O arquivo `.github/workflows/android.yml` está configurado para executar **sem precisar configurar um servidor**.

## Primeira publicação (sem terminal)

1. Crie um repositório no GitHub chamado `NEXIR-LAB` e deixe vazio, sem inicializar com README.
2. Extraia o ZIP recebido. Envie **o conteúdo da pasta extraída**, não o ZIP isoladamente, e preserve a pasta `.github/workflows`.
3. Crie um commit na branch `main`.
4. No repositório, abra **Actions**. Se solicitado, autorize os workflows.
5. A workflow **Android — Compilar e publicar APK** deve ser executada automaticamente.
6. Se houver execução concluída com símbolo verde, consulte **Releases > NEXIR LAB — APK automático (teste) > Assets > NEXIR-LAB.apk**.

**Importante:** no GitHub, em **Settings > Actions > General > Workflow permissions**, confirme que sua política permite gravar conteúdos e Releases. O YAML já declara `contents: write`, mas políticas organizacionais podem restringir este recurso. Se for impedido de publicar Release, o APK ainda ficará nos **Artifacts** da execução que teve compilação bem-sucedida.

## Atualizações

A cada `git push` à `main`, o GitHub faz:

1. Validação da biblioteca e da estrutura do projeto.
2. Instalação de JDK 17, SDK 35 e Gradle 8.9.
3. Testes Kotlin e geração de APK de desenvolvimento.
4. Upload do APK na execução do Actions.
5. Criação ou atualização do Release de testes `continuous` com `NEXIR-LAB.apk` e checksum SHA-256.

Pull requests executam testes e geram artefatos, mas **não publicam Releases**.

## Publicação de uma versão específica

Crie uma tag `v1.1.1`, `v1.2.0` etc. e envie ao GitHub. O workflow também publicará um Release próprio para essa tag. **A versão interna do app deve ser atualizada separadamente** em `app/build.gradle.kts` (`versionCode` e `versionName`).

## Assinatura de produção (recomendação antes de distribuir publicamente)

O APK desta automação está assinado pelo gerador **debug** do Android. Ele **pode ser instalado no Android**, mas não é equivalente a um release de produção. Cada runner pode gerar sua própria chave debug, portanto não se garante atualização por cima de builds anteriores.

Para lançar versões atualizáveis, configure uma keystore de produção persistente, armazene-a em GitHub Actions Secrets, habilite um fluxo `assembleRelease` com assinatura e guarde a keystore em backup seguro. Isso **não foi habilitado automaticamente**, pois gerar uma chave privada pública dentro do repositório seria inseguro.

## Se algo falhar

Abra **Actions > execução vermelha > etapa que falhou** para ver o log. Este projeto é entregue sem APK pré-compilado porque não há SDK Android nem downloads de Gradle disponíveis no ambiente de autoria; a primeira compilação real precisa ser confirmada no GitHub Actions.
