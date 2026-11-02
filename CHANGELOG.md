# Changelog

Todas as mudanças relevantes do Zentro. Segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [2.0.0] - 2026-11-02

### Visual novo
- **Nova identidade visual** no tema grafite (escuro), com a fonte Inter, ícones de traço fino e o novo logo "Z", também nos ícones do app no Windows, Mac, Linux e Android.
- **PC:** barra lateral com as seções, as pastas das notas (com contagem), a situação da sincronização e o seu perfil. Na barra de cima ficam o caminho, a busca, os avisos e o botão "Novo item".
- **Celular:** barra de abas embaixo e cabeçalho próprio em cada tela, com o botão "+".
- **Hoje:** saudação, captura rápida, linha do tempo dos compromissos, próximas contas e anotações recentes. No PC, ao lado, ficam o calendário do mês, as finanças do mês e o atalho para anotar uma ideia.
- **Agenda:** calendário com um pontinho nos dias com compromisso, filtros e "Ver semana".
- **Notas:** cartões das pastas, nota fixada em destaque e tarefas que dá para marcar direto na lista.

### Novidades
- **Captura rápida única:** escreva qualquer coisa na tela Hoje. "45,90 almoço" vira gasto, "reunião amanhã às 14h" vira compromisso e o resto vira anotação.
- **Categorias nos compromissos:** Trabalho, Faculdade e Pessoal, com etiqueta colorida e filtro na Agenda.
- **Busca global (Ctrl+K):** acha compromissos, notas, movimentações e cartões de uma vez. No celular, use a lupa da tela Hoje.
- **Sininho de avisos:** contas e faturas atrasadas ou vencendo, a revisão do dia e o próximo compromisso.
- **Pastas na barra lateral do PC**, com "+ Nova pasta".
- **Cartões de crédito:** cadastre cada cartão com o dia em que a fatura fecha, o dia em que vence e o limite (opcional).
  - Lance os gastos à vista ou parcelados. Cada parcela cai na fatura certa, e compras feitas a partir do dia do fechamento vão para a fatura seguinte.
  - Veja fatura por fatura, com cada gasto, o número da parcela, o total e o limite usado.
  - "Marcar como paga" lança o valor da fatura como saída e desconta do saldo. Dá para desfazer.
- **Saldo acumulado:** o que sobra num mês continua no seguinte, como na conta do banco. Compras no cartão só descontam quando a fatura é paga.
- **Saldo no fim de cada mês:** histórico com quanto você terminou cada mês e o resultado do mês.
- **Ajustar saldo:** informe quanto você tem hoje e o Zentro lança a diferença como "Ajuste de saldo".
- **Números de linha também no leitor das notas**, e não só no editor.

## [1.5.3] - 2026-10-30

### Novidades
- **Buscar e substituir nas notas:** Ctrl+F abre a busca e Ctrl+R (ou Ctrl+H) a troca. Enter vai para a próxima ocorrência, Shift+Enter volta e Esc fecha. Todas as ocorrências ficam marcadas, com a contagem ("3 de 12"). Dá para trocar uma por vez ou todas de uma vez. No celular, use a lupa no topo da nota.
- **Números de linha** à esquerda do editor de notas no PC, com a linha do cursor em destaque.
- **Barra de rolagem arrastável** no PC: nas notas, no editor, em Hoje, Agenda, Finanças e Ajustes. Não é mais preciso depender só da rodinha do mouse.

## [1.5.2] - 2026-10-30

### Correções
- **Windows:** abrir o Zentro pela busca, pelo atalho ou pelo menu Iniciar com ele já aberto (minimizado ou na bandeja) trazia uma segunda cópia. Agora a janela que já está aberta aparece na frente. A trava que impede duas cópias às vezes se soltava sozinha depois de um tempo; isso foi corrigido.
- Clicar no ícone da bandeja também desminimiza a janela.

## [1.5.1] - 2026-10-29

### Novidades
- **Esqueceu a senha?** No console do servidor, `senha seu@email.com` cria uma senha temporária; `contas` lista os e-mails cadastrados.
- **Trocar senha** em Ajustes › Conta.
- **Colar imagens no PC:** além de prints, o Ctrl+V agora também cola arquivos de imagem copiados no Explorer.

### Correções
- Ao entrar de novo na conta, o app oferece enviar as alterações feitas enquanto o servidor estava fora do ar (antes só oferecia se a conta estivesse vazia). A cópia local agora também guarda as exclusões pendentes.

## [1.5.0] - 2026-10-29

### Novidades
- **Tabelas nas notas:** tabelas em Markdown (`| coluna | coluna |` + `|---|---|`), como as que o Claude e o ChatGPT escrevem, viram tabelas de verdade, com cabeçalho, alinhamento e rolagem lateral. Botão de tabela na barra do editor.
- **Imagens nas notas:**
  - Botão de imagem no editor (arquivo no PC, galeria no Android) e Ctrl+V para colar prints no PC.
  - Fotos grandes são reduzidas antes do envio, ficam no servidor e em cache no aparelho, e abrem em tela cheia ao tocar.
- **Claude e ChatGPT lendo suas notas (MCP):** o servidor tem um endpoint MCP somente leitura com as ferramentas `search`, `fetch` e `list_notes`.
  - Em Ajustes › Claude e ChatGPT ficam o comando do Claude Code e o endereço do ChatGPT, prontos para copiar.
  - Depois é só pedir numa conversa: "faça o que está na nota Bed Wars". A IA recebe o texto, as tabelas e as imagens.

### Observações
- O ChatGPT só aceita conectores em endereços `https://`; o Claude Code funciona também em `http://`.

## [1.2.0] - 2026-10-16

### Novidades
- **Contas a pagar:** qualquer compromisso pode virar uma conta com valor e categoria (ex.: hospedagem todo dia 5).
  - Avisa um dia antes e no dia, com o valor na notificação.
  - O botão **Pagar** lança a saída nas finanças e para os avisos daquele mês.
  - Finanças ganhou "Contas do mês" (a pagar, pago, atrasada) e a tela Hoje fixa no topo as contas atrasadas.
  - Entrada rápida: `Hospedagem todo dia 5 49,90`, `toda segunda`, `todo dia`, `dias úteis`.
- **Backups automáticos no servidor:** ao iniciar, todo dia às 03:30 e antes de cada atualização, guardando os 20 mais recentes em `data/backups`. Também pelo comando `backup` no console.
- **Proteção de dados no app:** sair da conta ou ter a sessão expirada guarda uma cópia local. Ao entrar numa conta vazia, o app oferece recuperar os dados. Em Ajustes há "Enviar meus dados de novo".
- **Windows:**
  - A barra de título acompanha as cores do app (modo escuro e claro).
  - Novo menu da bandeja, com o próximo compromisso e "Iniciar com o Windows".
  - O app se atualiza também com ele aberto, assim que estiver minimizado ou na bandeja.

### Melhorias
- Textos técnicos (servidor, sincronização, tokens) foram trocados por mensagens simples.
- O servidor registra no console onde estão os dados e quantos itens existem, para facilitar a conferência.

## [1.1.1] - 2026-10-16

### Correções
- **Android:** o app não conectava a servidores acessados por `IP:porta` em HTTP (o Android 9+ bloqueia HTTP sem criptografia por padrão). Agora conecta.
- **Atualizações:** se o servidor não estiver acessível, os apps consultam as releases direto no GitHub, então uma falha de conexão não impede mais uma correção de chegar.

## [1.1.0] - 2026-10-16

### Novidades
- **Anotações estilo Notion/Obsidian:** pastas e subpastas, Markdown, `[[links]]` com sugestão enquanto digita, "mencionada em", `#tags`, checklists clicáveis, busca, modelos (Reunião, Aula, Projeto, Ideia), nota do dia, visão de tarefas abertas e mapa de conexões.
- **Agenda de outras pessoas:** marque compromissos de quem você acompanha (namorada, filho, sócio). Eles aparecem com o nome, têm filtro na agenda e também avisam, inclusive pelo Telegram. A entrada rápida aceita `Ana: prova sexta 10h`.
- **Atualização automática pelo GitHub:**
  - Servidor: um supervisor baixa a versão nova, troca, reinicia, volta para a anterior se falhar e reinicia o servidor em caso de queda.
  - PC: baixa e instala ao abrir, e avisa quando há versão nova com o app aberto.
  - Android: baixa o APK e abre o instalador.
- Rota `/api/update` com a última release (em cache), usada pelos apps.
- Variável `ZENTRO_AUTO_UPDATE` no egg do Pterodactyl.

### Melhorias
- As linhas da agenda mostram todos os avisos do compromisso.
- O APK de release passa a ser assinado com uma chave fixa (segredos do repositório).
- A versão do app vem de uma única fonte (`gradle/libs.versions.toml`).

## [1.0.0] - 2026-10-16

- Primeira versão: agenda com avisos garantidos (alarmes exatos no Android, bandeja no PC, Telegram como reserva), cobrança diária para atualizar a agenda, finanças com lançamento rápido, importação do Mercado Pago, sincronização em tempo real e egg do Pterodactyl.
