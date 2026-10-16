# Changelog

Todas as mudanças relevantes do Zentro. Segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

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
