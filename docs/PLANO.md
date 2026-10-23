# Relógio — Plano do Projeto

> Versão 2 · 23 out 2026 · Nome final da app e package id a definir
> App de relógio open source para Android, centrada em **alarmes fiáveis e muito personalizáveis**.
> Dispositivo de referência para testes: OPPO Find X9 Pro (ColorOS 16 / Android 16). A app tem de funcionar em **qualquer Android suportado**, não só neste.

---

## 1. Objetivo e princípios

Uma app de relógio (alarmes, timers, cronómetro) que resolva o que as apps de sistema e as alternativas fazem mal: alarmes que tocam **sempre**, com controlo fino de cada um.

Princípios que orientam todas as decisões:

1. **Fiabilidade primeiro.** O alarme tem de tocar com o telemóvel bloqueado, com a app fechada, depois de reiniciar, em Doze, em qualquer fabricante.
2. **Para todos os Androids, não para um telemóvel.** Nada pode depender da OPPO. Comportamentos específicos de fabricantes (ColorOS, HyperOS, One UI, etc.) são tratados por camadas opcionais com alternativa genérica.
3. **Privacidade total.** Sem permissão de internet, sem analytics, sem contas. Tudo fica no dispositivo.
4. **Acessível.** Funciona com TalkBack; todos os gestos (arrastar para desligar, roda de snooze) têm alternativa acessível; alvos de toque com tamanho mínimo.
5. **Multilíngue desde o início.** Inglês (padrão) e português de Portugal. Nenhum texto escrito no código.
6. **Pronto para open source.** Código, comentários, commits e documentação técnica em inglês; testes automáticos; CI; nada de segredos no repositório.

---

## 2. Stack técnica

| Área | Escolha | Porquê |
|---|---|---|
| Linguagem | **Kotlin nativo** | Acesso direto a alarmes exatos, serviços, notificações e áudio |
| Interface | **Jetpack Compose + Material 3** (tema próprio, sem cores dinâmicas) | UI moderna; o tema é todo nosso |
| Versões | **minSdk 31 (Android 12)**, targetSdk 36 (Android 16) | Cobre a grande maioria dos telemóveis em uso; abaixo de 12 não há desfoque nativo nem as APIs modernas de alarmes |
| Base de dados | **Room** (com migrações e schema exportado) | Alarmes, grupos, layouts, timers, lixo |
| Definições | **DataStore** | Preferências da app |
| Datas e fusos | **java.time** (`ZonedDateTime`, `ZoneId`) | Fusos e mudanças de hora corretos |
| Injeção de dependências | Hilt | Padrão em Android |
| Vídeo de fundo | Media3 / ExoPlayer | Loop de vídeo sem som |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) | Padrão atual; versões num só sítio |

### Arquitetura

- Um módulo `:app`, organizado **por funcionalidade** (`alarm`, `timer`, `stopwatch`, `settings`, `trash`) mais pacotes partilhados (`core/designsystem`, `core/data`, `core/scheduling`, `core/audio`, `core/notifications`, `core/oem`). Multi-módulo só se o projeto crescer.
- **Lógica de agendamento pura** (cálculo da próxima ocorrência, recorrências, datas, fusos, saltar próxima, snooze) em Kotlin sem dependências de Android, com **testes unitários extensos**. É a parte onde um erro significa alguém não acordar.

### Como o alarme toca (o núcleo)

1. Cada alarme é agendado com **`AlarmManager.setAlarmClock()`**: prioridade máxima, funciona em Doze, mostra o ícone de alarme na barra de estado.
2. À hora certa, um `BroadcastReceiver` arranca um **serviço em primeiro plano** que toca o som, vibra e mostra uma **notificação de ecrã inteiro** (`fullScreenIntent`).
3. A notificação abre a **Activity do alarme por cima do ecrã de bloqueio** (`showWhenLocked` + `turnScreenOn`).
4. Reagendar tudo após `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` (direct boot), `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`.
5. **Direct boot**: os alarmes têm de tocar mesmo antes de o utilizador desbloquear o telemóvel após um reinício (dados mínimos de agendamento em armazenamento protegido do dispositivo).

### Permissões (e diferenças entre versões)

- Alarmes exatos: `USE_EXACT_ALARM` (Android 13+, concedida a apps de alarmes) e `SCHEDULE_EXACT_ALARM` com `maxSdkVersion="32"` para Android 12.
- `USE_FULL_SCREEN_INTENT`: no Android 14+ pode não estar concedida; a app verifica e leva o utilizador ao sítio certo.
- `POST_NOTIFICATIONS` (pedido em runtime no Android 13+), `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`.
- `FOREGROUND_SERVICE` + o tipo adequado (a validar na fase 1).
- `POST_PROMOTED_NOTIFICATIONS`: Live Updates dos timers (Android 16).
- **Sem `INTERNET`. Sem permissões de armazenamento**: toques, imagens e vídeos vêm dos seletores do sistema e são copiados para dentro da app.

### Fiabilidade por fabricante (crítico)

Vários fabricantes fecham apps em segundo plano de forma agressiva (OPPO/realme/OnePlus com ColorOS, Xiaomi com HyperOS/MIUI, Samsung, Huawei/Honor, Vivo…). A app terá um **ecrã "Configurar fiabilidade"** (no primeiro arranque e nas definições):

- Checklist com estado real (✓/✗) quando o Android permite verificar:
  - notificações ativadas;
  - notificações de ecrã inteiro permitidas;
  - alarmes exatos permitidos;
  - sem otimização de bateria (pedido direto do sistema).
- **Passos específicos do fabricante detetado** (arranque automático, bloquear nos recentes, etc.), com atalho direto para o ecrã certo quando existe e alternativa para as definições da app quando não.
- Para fabricantes sem instruções próprias: passos genéricos + ligação para dontkillmyapp.com.
- Tudo isto isolado em `core/oem`, para ser fácil acrescentar fabricantes.

---

## 3. Funcionalidades — Alarmes

### 3.1 Configuração base

- Hora e minutos, escolhidos com **rodas de deslocar (estilo iPhone)**; o teclado fica como alternativa. As mesmas rodas servem os timers (h/m/s). A roda AM/PM só aparece no formato de 12 h.
- **Recorrência**, uma de três:
  - **Uma vez**: próxima ocorrência dessa hora.
  - **Dias da semana**: qualquer combinação seg–dom (primeiro dia da semana segue a região do sistema).
  - **Datas específicas**: uma ou várias datas no calendário (ex.: 3, 10 e 17 nov).
- **Alarmes "Uma vez"** (só hora): por defeito, depois de tocar e de serem desligados, ficam na lista **desativados** e **não voltam a tocar** até serem ligados à mão.
- **Opção "Utilização única"** (interruptor, desligado por defeito): o alarme toca na próxima ocorrência e, **assim que é desligado, vai para o caixote do lixo**.
- **Alarmes com datas apagam-se sozinhos depois da última data.**
- Quando um alarme é apagado (pelo utilizador ou automaticamente), o pop-up "Anular" tem também um **×** para o fechar logo.
- Quando um alarme é apagado automaticamente (datas ou utilização única):
  - vai para o **caixote do lixo**;
  - **notificação**: "Este alarme tocou pela última vez e foi apagado — toca para anular";
  - dentro da app, **pop-up discreto** com "Anular", que desaparece se a app for fechada e reaberta sem lhe mexer.
- Etiqueta personalizada.
- Toque escolhido do armazenamento (ou toques do sistema).
- Vibração: nenhuma, sincronizada com o toque, ou 4–5 padrões pré-definidos.
- Fuso horário próprio (opcional), ver 3.6.
- Layout do ecrã do alarme, ver 3.7.

### 3.2 Som e volume

- Usa o **canal de volume de alarme do sistema** (stream de alarme), separado da música e do toque.
- Comportamento normal do Android: o modo silêncio não corta alarmes; só o volume de alarme a zero os silencia.
- **Aviso na app** quando o volume de alarme está a zero e há alarmes ativos.
- **Volume gradual** (por alarme, desligado por defeito): volume final em % do volume de alarme; duração da subida em segundos.
- **Duração do toque** (por alarme, 1 min por defeito): ao fim desse tempo o alarme **para** e fica uma **notificação de alarme perdido** (só notificação).
- **Saída de áudio** (definição geral): **telemóvel + Bluetooth** (padrão), só telemóvel, só Bluetooth (se nada estiver ligado, toca no telemóvel).
  - "Telemóvel + Bluetooth" toca dois leitores: o alarme no altifalante (volume de alarme, passa o Não incomodar) e uma cópia no Bluetooth como multimédia (volume de multimédia). O encaminhamento normal do Android, testado num Samsung, tocava só no altifalante.
- Desde o Android 9 o volume de alarme não desce abaixo de 1, por isso o aviso de "volume a zero" raramente aparece.

### 3.3 Ecrã do alarme (interação)

- **Desligar**: arrastar para o lado.
- **Snooze**: **toque simples** → duração padrão (**5 min**, configurável por alarme, em lote e nas predefinições); **pressão longa** → abre um **dial** em meia-lua à volta do botão com **5, 10, 15, 20, 30, 45 min, 1 h**: sem levantar o dedo, desliza-se até ao tempo e **larga-se para adiar** (largar junto ao botão cancela). Um só gesto, rápido. **Sem limite** de snoozes. Com TalkBack, "Escolher quanto tempo adiar" abre uma roda acessível.
- **Acessibilidade**: com TalkBack, desligar e snooze ficam disponíveis como ações diretas, sem gestos.
- Se o alarme tem fuso diferente do local, mostra **as duas horas**.
- **Botão de baixar volume** (definição, desligado por defeito): enquanto o alarme toca, carregar em "baixar volume" **cala só aquele toque** — só o som, ou som e vibração, conforme a definição — sem desligar o alarme nem o adiar. O toque seguinte (snooze ou outro alarme) é normal.

### 3.4 Avisos antes do alarme

- Notificação antes do alarme com **"Desligar este"**.
- Por defeito **30 min antes**; nas definições podem adicionar-se vários tempos (ex.: 1 dia, 1 h, 30 min, 5 min).
- **Uma só notificação por alarme, atualizada** ("daqui a 30 min" → "daqui a 5 min").
- Desligar antecipadamente: recorrente → cancela só essa ocorrência; datas → conta como cumprida (se era a última, vai para o lixo); utilização única → vai para o lixo.

### 3.5 Saltar próxima ocorrência

- Em alarmes recorrentes, **"Saltar próxima"**: não toca da próxima vez e volta ao normal depois. Visível no cartão e reversível.

### 3.6 Fusos horários

- **Definição geral**: Automático (padrão) ou Manual (todos os alarmes seguem um fuso escolhido; ex.: em viagem, continuar à hora de casa).
- **Por alarme**: fuso próprio, com **prioridade sobre o geral** (ex.: acordar à mesma hora que alguém noutro país).
- Contas corretas, incluindo mudanças de hora de verão dos dois lados.
- Na lista: "8:00 Nova Iorque · 13:00 aqui" (com o dia da semana quando o dia aqui é outro: "dom 22:00 aqui"). A hora "aqui" só aparece quando as horas são diferentes; um alarme com fuso próprio mostra sempre a cidade. O mesmo no editor (por baixo das rodas), nas notificações e no ecrã do alarme.
- Escolha do fuso por cidade ou país, com nomes no idioma do telemóvel (dados ICU do sistema, nada é descarregado).
- Com fuso manual, "aqui" continua a ser o fuso do telemóvel.

### 3.7 Layouts do ecrã do alarme

- **Editor de layout**: arrastar e redimensionar **relógio**, **etiqueta** e **botões**; cores de cada elemento; vários **estilos de relógio**; **fundo** (foto, vídeo em loop sem som, ou preset).
- **Proteções**: botões com tamanho mínimo e sempre dentro do ecrã; o layout adapta-se a ecrãs de tamanhos e proporções diferentes (posições relativas, não em píxeis).
- **"Testar alarme"** para pré-visualizar a sério.
- **Biblioteca de layouts** com nome; cada alarme escolhe um ou cria um novo; **layout padrão** nas definições.
- **Presets incluídos**: fundos abstratos e animações em código (pontos, ondas, matriz de pontos). Só recursos feitos para o projeto ou com licença livre compatível.
- Implementado: elementos relógio, data, etiqueta e botões (adiar + deslizar); estilos de relógio pontos, mono e analógico; fundos pontos, ondas, matriz, cor, gradiente, foto e vídeo (copiados para a app; a foto respeita a orientação EXIF; o vídeo toca sem som com `MediaPlayer`, sem bibliotecas extra).
- Layouts incluídos (Pontos, Ondas, Matriz de pontos, Analógico) não se editam: duplicam-se. Apagar um layout devolve os alarmes que o usavam ao predefinido.
- Se num ecrã pequeno ou com letra grande dois elementos se tocam, o de baixo desce até deixar de tocar.
- No editor, com TalkBack, cada elemento tem ações para mover e mudar de tamanho, sem gestos.
- Relógios: estilos digital, empilhado, analógico com pontos e analógico com traços; 7 letras (Doto, JetBrains Mono, Outfit, Playfair Display, Orbitron, Big Shoulders Display, Comfortaa, todas OFL e variáveis); espessura ajustável e, na Doto, pontos mais ou menos redondos.
- A app fica sempre na vertical (o Android 16 pode ignorar isto em ecrãs grandes, ≥ 600 dp).
- Cores: seletor completo (tom, saturação/brilho e opacidade) com cores rápidas e "cor automática".
- Foto e vídeo: no separador Fundo, arrastar e afastar os dedos posiciona e amplia (sem nunca deixar margens vazias). Elementos escondidos desaparecem também no editor.
- "Testar" abre o ecrã do alarme verdadeiro com o que está no editor (sem guardar), sem som; adiar ou desligar só fecham a pré-visualização.
- Um layout novo ou duplicado é um rascunho até se carregar em Guardar; sair com alterações por guardar pede confirmação.
- Layouts apagados vão para o caixote do lixo; ao restaurar, os alarmes que os usavam (e o predefinido) voltam a usá-los.
- Se o alarme começa a tocar com o telemóvel em uso e o ecrã é bloqueado, o ecrã do alarme aparece (a notificação é publicada de novo, porque o Android só abre o ecrã inteiro para notificações novas).

### 3.8 Organização e lista principal

- **Lista principal mistura alarmes soltos e grupos**, cartões do mesmo tamanho:
  - **Alarme**: hora, recorrência/datas, etiqueta, interruptor.
  - **Grupo**: nome, interruptor, expandir/recolher e o **próximo alarme completo** ("próximo: qui, 3 nov, 7:30").
- **Ordenação**: pelo próximo a tocar (data + hora reais, com fusos). Grupos posicionados pelo seu próximo alarme. Desativados no fundo, também por ordem.
- Cada alarme pertence a **no máximo um grupo**.
- **Interruptor do grupo** como interruptor mestre (não altera o estado individual de cada alarme).

### 3.9 Seleção, edição em lote e apagar

- **Pressão longa** num cartão → seleção múltipla.
- Ações em lote: apagar, ligar/desligar, mover para grupo, **editar em lote** (cada campo começa em "não alterar").
- **Apagar grupo**: só o grupo (alarmes ficam soltos) ou grupo + alarmes.

### 3.10 Caixote do lixo

- Alarmes e grupos apagados (incluindo os automáticos).
- **Últimos 50 itens**; apagados de vez ao fim de **30 dias**.
- Grupos apagados com alarmes ficam **organizados como grupo**; recupera-se o grupo com um, vários ou todos os alarmes.
- Pode esvaziar-se à mão.

### 3.11 Colisões

- Ao **guardar** um alarme que coincide com outro, **aviso** nesse momento.
- Se tocarem juntos: **um único ecrã** com as etiquetas de ambos, com som e layout do criado primeiro.

---

## 4. Timers

- **Lista de timers guardados** (começa vazia): criar, editar (nome, h/m/s), apagar. Até **24 h**. Sem atalhos pré-definidos.
- **Vários a contar ao mesmo tempo**; na notificação ficam agrupados.
- **Quando acaba**: ecrã bloqueado/apagado → **ecrã inteiro** como um alarme; telemóvel em uso → **notificação pop-up persistente** com som até ser desligada.
- **Live Updates (Android 16)** para mostrar a contagem nas superfícies do sistema; em versões anteriores, notificação contínua normal. Superfícies de fabricante (ex.: Fluid Cloud da OPPO, Now Bar da Samsung) aproveitam-nas quando o sistema as liga; a validar no dispositivo de referência.

- Implementado (fase 7): fim do timer marcado no `AlarmManager` com `elapsedRealtime` (imune a mudanças manuais da hora) e hora real guardada para sobreviver a reinícios; tocar usa um serviço próprio (como os alarmes) com som até parar ou 10 min; "+1 min"; com a app aberta abre logo o ecrã "Tempo esgotado"; se a app morrer a tocar, volta a tocar quando arranca.

## 5. Cronómetro

- Iniciar, pausar, retomar, reiniciar.
- Voltas, com destaque da mais rápida e da mais lenta.
- Baseado em timestamps (continua certo com a app fechada), com notificação enquanto corre; candidato a Live Update.

### 5.1 Relógio mundial (acrescentado durante a fase 7)

- Separador próprio: **Alarmes · Relógio mundial · Timers · Cronómetro**.
- Lista de cidades escolhidas (começa vazia), com o mesmo seletor de fusos dos alarmes (pesquisa por cidade ou país, nomes no idioma do telemóvel).
- Cada cidade mostra a **hora atual**, a **diferença para aqui** ("+5 h", "−3 h 30") , **dia ou noite** e a **data quando é outro dia**.
- Reordenar e apagar; atalho para **criar um alarme no fuso dessa cidade**.
- Implementado (fase 8): "dia" é das 7h às 19h locais (regra simples, sem localização); a barra de baixo tem 4 botões da mesma largura e os nomes longos encolhem para caber.

---

## 6. Definições da app

| Definição | Padrão |
|---|---|
| Toque padrão | toque de alarme do sistema |
| Duração do toque padrão | 1 min |
| Volume gradual padrão | desligado (volume final e duração configuráveis) |
| Vibração padrão | sincronizada com o toque |
| Snooze padrão | 5 min |
| Avisos antes do alarme | 30 min (lista editável) |
| Layout padrão do ecrã do alarme | preset da app |
| Saída de áudio | telemóvel + Bluetooth |
| Botão de baixar volume a tocar | não fazer nada (ou calar o som, ou calar som e vibração) |
| Formato de hora | seguir o sistema (ou fixar 12 h / 24 h) |
| Fuso horário | automático (ou manual) |
| Som dos timers | toque padrão |
| Tema | seguir o sistema (ou fixar escuro/claro) |
| Cor de destaque | vermelho (personalizável) |
| Reduzir transparência | desligado (e ligado automaticamente com a poupança de bateria; o Android não tem uma definição de "reduzir transparência") |
| Idioma | seguir o sistema (seletor por app do Android 13+) |
| Configurar fiabilidade | ecrã de fiabilidade |
| Caixote do lixo | acesso ao lixo |

---

## 7. Design

**Base: estética Nothing. Matéria: Liquid Glass.**

- **Preto e branco**; **modo escuro** (preto puro, ótimo em OLED) e **modo claro** (inversão direta).
- **Uma cor de destaque**, vermelho por defeito e personalizável, só em pontos de ação (interruptores ativos, "+", seleção, botões principais). Garantir contraste suficiente em ambos os modos.
- **Tipografia** (licenças livres, ficheiros de licença incluídos no repo):
  - horas em **matriz de pontos**: **Doto** (OFL);
  - restante texto **monoespaçado**: Space Mono ou JetBrains Mono (OFL).
  - Não usar as fontes proprietárias da Nothing.
- Respeitar o tamanho de letra do sistema sem partir layouts.
- Ícones de traço fino, detalhes com pontos, muito espaço vazio.
- **Navegação**: barra inferior **Alarmes · Relógio mundial · Timers · Cronómetro**, distribuída de canto a canto; definições no topo.

### Liquid Glass

- O conteúdo base é plano e sólido (Nothing); o **vidro só nas camadas que flutuam**: botões, roda de snooze e etiqueta no ecrã do alarme (por cima da foto/vídeo); barra de navegação flutuante em cápsula; botão "+"; bottom sheets, menus e o pop-up "Anular"; barra de ações da seleção múltipla.
- Cartões da lista e texto principal ficam sólidos, para leitura nítida.
- Efeito: desfoque do fundo, refração leve nas bordas, reflexo subtil no contorno, animações com ligeiro "derreter".
- Técnica: desfoque com `RenderEffect` (Android 12+) / biblioteca Haze; refração com shaders AGSL (`RuntimeShader`, Android 13+). **No Android 12 só desfoque, sem refração.** Avaliar bibliotecas da comunidade (manutenção ativa, licença compatível).
- **"Reduzir transparência"** troca o vidro por superfícies sólidas.

---

## 8. Modelo de dados (resumo)

- **Alarm**: id, hora, minuto, tipoRecorrência (ONCE / WEEKDAYS / DATES), diasSemana, datas[], etiqueta, grupoId?, ativo, toqueUri, vibração, gradual (ativo, volumeFinal, duração), duraçãoToque, fuso?, layoutId?, saltarAté?, utilizaçãoÚnica, criadoEm
- **AlarmGroup**: id, nome, ativo, expandido
- **Layout**: id, nome, fundo (tipo + uri/preset), elementos (posição relativa, tamanho, cor, estilo) em JSON
- **Timer**: id, nome, duração, estado, fimPrevisto, restante
- **TrashItem**: id, tipo (alarme/grupo), snapshot JSON, apagadoEm
- **Definições** em DataStore

---

## 9. Fases de desenvolvimento

Cada fase termina com um **APK instalado e testado** no dispositivo de referência (e em emuladores de outras versões quando a fase o justifica).

| Fase | Conteúdo | Teste |
|---|---|---|
| **0. Base** | Projeto Gradle, version catalog, tema Nothing, fontes, protótipo dos componentes de vidro, navegação, Room, DataStore, strings EN + pt-PT, assinatura (keystore fora do repo), CI (build + lint + testes), `CLAUDE.md`, `.gitignore` | App abre, navegação, claro/escuro, idiomas |
| **1. Núcleo do alarme** ⚠️ | Lógica pura de agendamento + testes unitários, `setAlarmClock`, receiver, serviço, ecrã inteiro sobre o bloqueio, desligar, snooze simples, reagendar (boot, direct boot, hora, fuso, update), permissões por versão, ecrã de fiabilidade com camada por fabricante | **Teste da noite**: ecrã bloqueado durante horas, após reiniciar, com a app fechada |
| **2. Recorrência e ciclo de vida** | Dias da semana, datas, utilização única, auto-apagar + anular, caixote do lixo, alarme perdido, saltar próxima, avisos antes | Datas múltiplas, anular, lixo |
| **3. Áudio** | Toques, vibrações, volume gradual, duração do toque, saída de áudio, aviso de volume a zero | Com e sem Bluetooth, volume a zero, silêncio e Não Incomodar |
| **4. Lista e organização** | Grupos, cartões, ordenação, interruptor de grupo, seleção múltipla, edição em lote, apagar grupo, colisões | Muitos alarmes |
| **5. Fusos horários** | Fuso por alarme, fuso manual geral, dupla hora | Mudar o fuso à mão; mudança de hora |
| **6. Ecrã do alarme** | Roda de snooze, editor e biblioteca de layouts, fundos, animações, testar alarme, alternativas acessíveis | Editor, vídeo, TalkBack, ecrãs de tamanhos diferentes |
| **7. Timers e cronómetro** | Timers guardados e múltiplos, ecrã inteiro vs pop-up, cronómetro, Live Updates | Vários timers, bloqueado vs em uso |
| **8. Relógio mundial** | Separador novo (5.1): cidades, diferenças, dia/noite, alarme no fuso da cidade | Vários fusos, mudança de hora |
| **9. Definições e acabamento** | Todas as definições, cor de destaque, transições, ícone | Revisão geral |
| **10. Validação e publicação** | Bateria de testes completa (secção 10), README em inglês, licença, primeira release | Alguns dias de uso real |

---

## 10. Testes

**Automáticos**
- Testes unitários da lógica de agendamento: todas as recorrências, datas, saltar próxima, snooze, fusos, mudanças de hora de verão (incluindo horas que não existem ou se repetem), viragem de ano.
- Testes de migração do Room.
- CI no GitHub Actions em cada push/PR.

**Manuais** — dispositivo de referência + emuladores API 31, 33, 34 e 36.

- [ ] Ecrã bloqueado e apagado durante várias horas
- [ ] App removida dos recentes
- [ ] Doze forçado (`adb shell dumpsys deviceidle force-idle`)
- [ ] Depois de reiniciar, **antes de desbloquear** (direct boot)
- [ ] Modo silêncio e Não Incomodar
- [ ] Volume de alarme a zero (aviso)
- [ ] Bluetooth ligado, nos 3 modos de saída
- [ ] Mudança manual de fuso e de hora
- [ ] Dois alarmes à mesma hora
- [ ] Alarme durante uma chamada
- [ ] Poupança de energia / bateria fraca
- [ ] Permissão de ecrã inteiro ou de alarmes exatos retirada
- [ ] TalkBack e letra grande

> ⚠️ **Assinatura**: usar sempre a mesma chave. A keystore e as passwords **nunca** entram no repositório (`keystore.properties` local, ignorado pelo git; na CI via secrets). Mudar de chave obriga a desinstalar e perde os alarmes.

---

## 11. Open source e distribuição

- **Licença**: a decidir antes de tornar o repositório público.
- **Distribuição**: APK assinado nas GitHub Releases; mais tarde, F-Droid e/ou Google Play (o Play aceita `USE_EXACT_ALARM` para apps de alarme).
- README e CONTRIBUTING em inglês; este plano fica em português como documento de produto.
- Nome da app e package id a definir (o nome do repositório no GitHub perdeu o acento: "Rel-gio").

## 12. Pontos a validar durante a implementação

- Tipo de serviço em primeiro plano mais adequado para o alarme.
- Comportamento de "telemóvel + Bluetooth" em vários fabricantes.
- Live Updates nas superfícies de fabricante (Fluid Cloud, Now Bar).
- Atalhos diretos para os ecrãs de arranque automático de cada fabricante.
- Desempenho do vidro (sobretudo sobre vídeo) em telemóveis modestos; biblioteca vs shader próprio.

## 13. Decisões tomadas (podem mudar)

- Interruptor do grupo é mestre: não altera o estado individual dos alarmes.
- "Desligar este" num aviso antecipado conta como ocorrência cumprida.
- Nas colisões, manda o alarme criado primeiro.
- Snooze reinicia a duração do toque completa.
- minSdk 31; sem permissão de internet; inglês como idioma padrão.
- Fora da v1: relógio mundial, widgets, backup/exportação.
