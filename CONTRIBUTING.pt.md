<p align="right"><a href="CONTRIBUTING.md">English</a> · <b>Português</b></p>

# Contribuir para o Clock

Obrigado pela ajuda. O Clock tem uma tarefa acima de todas, **alarmes que tocam sempre**, por isso
as contribuições mais úteis são as que tornam os alarmes mais fiáveis em mais telemóveis.

## Como podes ajudar

- **Reporta um alarme que não tocou** (ou tocou atrasado). Usa o
  [relatório de bug](https://github.com/iM-lemoS/Clock/issues/new?template=bug_report.yml) e
  indica o modelo do telemóvel, a versão do Android, a camada do fabricante (ColorOS, One UI,
  HyperOS…), o que o telemóvel estava a fazer (bloqueado, poupança de energia, acabado de
  reiniciar) e o que aparece em **Definições > Fiabilidade dos alarmes**.
- **Partilha como um fabricante fecha as apps.** Os passos que mantêm os alarmes a funcionar numa
  marca vão para `core/oem`, e todos os utilizadores dessa marca passam a tê-los.
- **Traduz.** Os textos estão em `app/src/main/res/values*/strings.xml`. Abre primeiro uma issue
  para combinarmos o idioma.
- **Programa.** Escolhe uma issue aberta, ou abre uma a descrever a alteração antes de começares
  algo grande.

Podes escrever issues e pull requests em português ou em inglês.

## Regras base

São verificadas na revisão, e várias são impostas pela compilação:

- **Sem rede, sem rastreio.** Sem permissão `INTERNET`, sem analytics, sem dependências que falem
  com a rede, sem permissões de armazenamento (usa os seletores do sistema e copia para dentro da
  app). Caso contrário, o `verify<Variant>Permissions` faz a compilação falhar.
- **Nenhum texto no código.** Todos os textos visíveis vão para `values/strings.xml` (inglês)
  **e** `values-pt/strings.xml` (português europeu, a tratar o utilizador por "tu"). Traduções em
  falta fazem o lint falhar.
- **Código específico de fabricantes só em `core/oem`**, atrás do `OemProfile`, com alternativa
  genérica.
- **A matemática do agendamento é Kotlin puro** em `:core:scheduling` (sem dependências de Android)
  e vem com testes unitários: próxima ocorrência, recorrências, fusos e hora de verão são o sítio
  onde um erro significa alguém não acordar.
- **Alterações à base de dados**: depois da primeira release, cada mudança ao esquema sobe a versão
  do Room e traz uma migração e um teste em `AppDatabaseMigrationTest`.
- **Versões** só em `gradle/libs.versions.toml`; usa as últimas versões estáveis.
- **Inglês** no código, comentários, identificadores, mensagens de commit e documentação técnica.
  O `docs/PLANO.md` (plano do produto) fica em português.

## Primeiros passos

Requisitos: JDK 17+ (o JBR do Android Studio serve) e o Android SDK com a plataforma 37.

| Tarefa | Comando |
|---|---|
| APK de debug | `./gradlew assembleDebug` |
| Instalar num dispositivo | `./gradlew installDebug` |
| Testes unitários | `./gradlew testDebugUnitTest :core:scheduling:test` |
| Lint (avisos são erros) | `./gradlew lintDebug :core:scheduling:lint` |
| Verificação de permissões | `./gradlew verifyDebugPermissions verifyReleasePermissions` |
| Testes no dispositivo (migrações do Room) | `./gradlew connectedDebugAndroidTest` |

A CI corre a compilação, o lint, os testes unitários e a verificação de permissões em cada push e
pull request.

As builds de debug instalam-se ao lado da release (`io.github.imlemos.clock.debug`) e trazem
comandos adb para testar alarmes com o telemóvel bloqueado:

```sh
C=io.github.imlemos.clock.debug/io.github.imlemos.clock.debug.DebugCommandsReceiver
adb shell am broadcast -a io.github.imlemos.clock.debug.TEST_ALARM -n $C --ei in_minutes 1 --es label "'Teste'"
adb shell am broadcast -a io.github.imlemos.clock.debug.START_TIMER -n $C --ei seconds 10 --es name Cha
```

## A arquitetura num minuto

```
:app                         app Android, organizada por funcionalidade
  alarm/ timer/ stopwatch/   ecrãs de cada funcionalidade
  worldclock/                separador do relógio mundial
  reliability/               ecrã de fiabilidade (permissões, bateria, passos por fabricante)
  trash/                     alarmes apagados, recuperáveis durante 30 dias
  alarm/layout/              layouts do ecrã do alarme: modelo, geometria, predefinições, desenho, editor
  settings/ navigation/      definições; chaves do Navigation 3 e a estrutura dos separadores
  core/designsystem/         tema, superfícies de vidro, componentes partilhados, háptica, animação
  core/data/                 base de dados Room, definições DataStore, módulos Hilt
  core/audio/                som e volume do alarme
  core/notifications/        canais e construção de notificações
  core/time/                 relógio de tempo decorrido e deteção de reinícios
  core/oem/                  comportamento específico de fabricantes atrás do OemProfile
  core/zones/                nomes de fusos traduzidos e o seletor de fusos
:core:scheduling             Kotlin puro: matemática do agendamento com testes JUnit
```

A interface é Jetpack Compose com um tema Material 3 próprio. Só as camadas flutuantes (navegação,
painéis, diálogos, controlos do ecrã do alarme) usam a superfície de vidro; o `GlassSurface` é o
único código que mexe na biblioteca de desfoque.

## Testar alarmes num telemóvel

Alterações ao agendamento, ao toque ou às notificações devem ser experimentadas num dispositivo
real. Cenários úteis: ecrã bloqueado, Doze forçado (`adb shell dumpsys deviceidle force-idle`), app
fechada (`adb shell am kill`), reinício, e antes do primeiro desbloqueio depois de reiniciar.
Confirma que o alarme está agendado com `adb shell dumpsys alarm | grep imlemos`. No pull request,
indica o dispositivo e o que testaste.

## Pull requests

- Pequenos e focados; uma alteração por commit, com uma mensagem descritiva (em inglês).
- Corre os testes unitários, o lint e a verificação de permissões antes do push.
- Acrescenta ou atualiza testes quando mexes no agendamento.
- Acrescenta os textos nas duas línguas e junta capturas de ecrã nas alterações de interface.
- Ao contribuíres, aceitas que o teu trabalho é publicado sob a licença do projeto.
