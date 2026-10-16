<div align="center">

<img src="composeApp/icons/zentro.png" width="96" alt="Zentro" />

# Zentro

**Agenda e finanças pessoais que não deixam você esquecer de nada.**
Android + Desktop (Windows, Linux, macOS) sincronizados em tempo real, com servidor próprio pronto para o Pterodactyl.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.8-4285F4?logo=jetpackcompose&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor-3.1-087CFA?logo=ktor&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-003B57?logo=sqlite&logoColor=white)

</div>

![Zentro no celular](docs/screenshots/mobile.png)

| Agenda no desktop | Finanças no desktop |
|---|---|
| ![Agenda](docs/screenshots/desktop-agenda.png) | ![Finanças](docs/screenshots/desktop-financas.png) |

## O que ele faz

- **Agenda simples e rápida.** Escreva `Dentista amanhã 14h` e pronto: o compromisso é criado com avisos. Também entende `reunião sexta 9:30`, `pagar luz 10/11`, `depois de amanhã`…
- **Avisos que não falham.** Várias camadas independentes (veja abaixo). Compromissos **críticos** tocam como despertador, em tela cheia, até você confirmar.
- **Cobra a atualização da agenda.** Nos horários que você escolher (padrão 09:00 e 20:00), se você ainda não mexeu na agenda naquele dia, o Zentro te lembra.
- **Finanças sem esforço.** `45,90 almoço` registra uma saída e `+1200 freela` uma entrada, com a categoria sugerida automaticamente. Saldo do mês, entradas, saídas e para onde foi o dinheiro.
- **Mercado Pago.** Importa automaticamente os pagamentos recebidos e feitos na sua conta.
- **Celular e computador conectados.** Mudou num, aparece no outro em segundos (WebSocket). Funciona offline e sincroniza quando a internet volta.
- **Seus dados no seu servidor.** Um `.jar` só, banco SQLite em arquivo, egg pronto para o Pterodactyl.

## Por que os avisos não falham

| Camada | Como funciona |
|---|---|
| **Alarme do sistema (Android)** | Os lembretes usam `AlarmManager.setAlarmClock`, o mesmo mecanismo do app Relógio, o único que o Android garante disparar no minuto exato mesmo em soneca (Doze) e economia de bateria. Os próximos 7 dias ficam agendados no aparelho. |
| **Funciona offline** | A agenda fica salva no aparelho. Sem internet, os alarmes continuam tocando. |
| **Reagendamento automático** | Os alarmes são refeitos quando a agenda muda, quando um alarme toca, a cada 15 min (WorkManager), todo dia às 04:00, ao ligar o celular, ao atualizar o app e ao mudar o relógio ou o fuso. |
| **Avisos perdidos** | Se o celular estava desligado, ao ligar ele mostra o que você perdeu nas últimas 6 horas. O desktop faz o mesmo ao abrir. |
| **Críticos** | Tela cheia por cima do bloqueio, som de alarme repetindo (`FLAG_INSISTENT`) e botões "Estou ciente" e "Adiar 5 min". No PC, uma janela fixa por cima de tudo com som em loop. |
| **Desktop sempre ativo** | Inicia junto com o sistema, fica na bandeja e mostra aviso flutuante e notificação do sistema. Fechar a janela não desliga os avisos. |
| **Telegram (reserva no servidor)** | Mesmo com o celular sem bateria ou o PC desligado, o servidor manda o lembrete pelo Telegram, que chega no celular, no relógio e no PC. |
| **Checklist no app** | Em Ajustes › *Avisos garantidos*, o app verifica permissões (notificação, alarme exato, bateria, tela cheia) e tem um botão para corrigir cada uma, além de *Testar aviso*. |

## Arquitetura

```mermaid
flowchart LR
    subgraph Apps["Compose Multiplatform (Kotlin)"]
        A[Android<br/>AlarmManager + WorkManager]
        D[Desktop<br/>Bandeja + agendador]
    end
    subgraph S["Servidor Ktor (Kotlin) · Pterodactyl"]
        API[REST + WebSocket]
        DB[(SQLite)]
        R[Agendador de avisos]
    end
    A <-->|sync + tempo real| API
    D <-->|sync + tempo real| API
    API --- DB
    R -->|lembretes reserva| TG[Telegram]
    S <-->|importação| MP[API Mercado Pago]
```

```
zentro/
├── shared/       # Kotlin Multiplatform: modelos, contrato da API, motor de recorrência e avisos, entrada rápida
├── server/       # Ktor + Exposed + SQLite: API, sincronização, Telegram, Mercado Pago
├── composeApp/   # App Compose Multiplatform
│   ├── commonMain/   # Telas, repositório local-first, fila offline, cliente HTTP
│   ├── androidMain/  # Alarmes, notificações, tela cheia, boot, WorkManager
│   └── desktopMain/  # Bandeja, avisos flutuantes, alarme crítico, autostart
├── pterodactyl/  # Egg pronto para importar
└── .github/      # CI e releases automáticas
```

**Destaques técnicos**

- **Um motor de agenda só** (`shared/Schedule.kt`) calcula recorrências e horários dos avisos para servidor, Android e desktop, com testes unitários.
- **Local-first com fila persistente.** Cada alteração é aplicada no aparelho na hora e vai para uma fila (`outbox`) que é reenviada até o servidor aceitar. Os IDs são UUIDs gerados no cliente, então reenviar nunca duplica.
- **Sincronização incremental** por `updatedAt` com *tombstones* (itens apagados também sincronizam) e aviso em tempo real via WebSocket.
- **Isolamento por usuário** com chave composta `(user_id, id)`, autenticação JWT e senhas com BCrypt.

## Instalação do servidor (Pterodactyl)

1. **Publique uma release.** Crie uma tag no GitHub (`git tag v1.0.0 && git push origin v1.0.0`). O GitHub Actions gera o `zentro-server.jar`, o APK e os instaladores do desktop.
2. **Importe o egg.** No painel: *Admin › Nests › Import Egg* e envie [`pterodactyl/egg-zentro.json`](pterodactyl/egg-zentro.json).
3. **Crie o servidor** com o egg *Zentro Server* (imagem Java 21). 256 MB de RAM são suficientes. Confira a variável **Repositório no GitHub** (`usuario/zentro`).
4. **Inicie.** Quando aparecer `Zentro server started` no console, está no ar. Abra `http://IP:PORTA` no navegador para conferir.
5. **Crie sua conta** pelo app e depois mude **Permitir novos cadastros** para `false`.

> Para atualizar: mude a variável *Versão* (ou deixe `latest`) e clique em **Reinstall**. Os dados em `data/` são preservados.
> Console do servidor: `status` mostra a versão e `stop` desliga com segurança.

### Variáveis

| Variável | Padrão | Descrição |
|---|---|---|
| `GITHUB_REPO` | `zyypj/zentro` | Repositório de onde baixar a release |
| `VERSION` | `latest` | Tag da release a instalar |
| `DOWNLOAD_URL` | vazio | Link direto para o `.jar` (opcional) |
| `ZENTRO_ALLOW_REGISTRATION` | `true` | Permite criar novas contas |
| `TELEGRAM_BOT_TOKEN` | vazio | Ativa os avisos reserva pelo Telegram |
| `ZENTRO_DATA_DIR` | `data` | Pasta do banco e da chave de sessão |

Sem Pterodactyl? `docker build -t zentro . && docker run -p 8080:8080 -v zentro-data:/app/data zentro`

## Telegram (recomendado)

1. No Telegram, fale com o [@BotFather](https://t.me/BotFather), envie `/newbot` e copie o token.
2. Cole o token na variável `TELEGRAM_BOT_TOKEN` do servidor e reinicie.
3. No app: *Ajustes › Conectar Telegram* abre o bot. Toque em **Iniciar** e pronto.
4. Comandos do bot: `/hoje` e `/amanha` mostram sua agenda.

## Mercado Pago

1. Acesse [mercadopago.com.br/developers/panel](https://www.mercadopago.com.br/developers/panel) e crie uma aplicação.
2. Em *Credenciais de produção*, copie o **Access Token** (`APP_USR-...`).
3. No app: *Ajustes › Mercado Pago*, cole o token e toque em **Conectar**.

A primeira importação traz os últimos 90 dias e depois atualiza sozinha a cada 15 minutos. Para importar na hora, cadastre a URL de webhook mostrada no app em *Webhooks › Pagamentos* no painel do Mercado Pago.

> **Limitação importante:** a API pública do Mercado Pago é voltada para vendedores. O Zentro importa os **pagamentos aprovados** em que você recebe (entradas, pelo valor líquido) e, quando a API permite, os que você paga (saídas). Algumas movimentações, como Pix enviados do saldo, transferências e rendimentos, podem não aparecer na API. Para essas, use o lançamento rápido (`45,90 almoço`).

## Instalando os apps

- **Android:** baixe o `Zentro.apk` da release e instale. Abra *Ajustes › Avisos garantidos* e deixe tudo verde.
- **Windows:** baixe o `.msi` (ou `.exe`). O app inicia com o Windows e fica na bandeja.
- **Linux / macOS:** `.deb` e `.dmg` na release.

No primeiro acesso, informe o endereço do servidor (ex.: `123.45.67.89:8080`), seu e-mail e senha.

## Desenvolvimento

Requer JDK 21. O Android SDK é opcional: sem ele, o build ignora o alvo Android automaticamente.

```bash
./gradlew :shared:jvmTest :server:test   # testes
./gradlew :server:run                     # servidor em http://localhost:8080
./gradlew :composeApp:run                 # app desktop
./gradlew :composeApp:assembleDebug       # APK (precisa do Android SDK)
./gradlew :composeApp:packageMsi          # instalador Windows
```

Commits seguem [Conventional Commits](https://www.conventionalcommits.org/pt-br/).

## Licença

[MIT](LICENSE)
