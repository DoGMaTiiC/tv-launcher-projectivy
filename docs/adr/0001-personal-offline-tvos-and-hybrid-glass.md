# 0001. Fork personal-first, 100% offline e política híbrida de Liquid Glass

Status: accepted

Decidimos priorizar o uso pessoal na TCL 65C6K (Android 14 / Google TV 14) mantendo o launcher 100% offline (sem permissão `INTERNET`) e adotando uma estratégia híbrida de vidro: superfícies pré-borradas com ótica especular em GPU para tudo o que anima com o D-pad, reservando `RenderEffect` nativo apenas para avaliação em overlays estáticos como o Control Center.

## Considered Options

- **Estratégia do fork:** Seguir estritamente as restrições de PR do upstream (`AGENTS.md`: teto de 576 KB no APK, zero mudanças no layout padrão e foco no menor diff) vs. priorizar fidelidade visual e ergonomia para a TCL 65C6K mantendo flags modulares para eventual extração futura.
- **Política de rede:** Adicionar permissão `INTERNET` para puxar catálogos TMDB e clima (como *Free My TV* e forks online) vs. manter execução 100% offline com ativos locais (`hero/`, wallpapers).
- **Renderização de vidro (Liquid Glass):** Usar `RenderEffect` em tempo real atrás de elementos móveis vs. apenas blur pré-processado vs. pré-blur no que se move (Focus Slab e Glass Dock) com `RenderEffect` opcional restrito a overlays estáticos na API 31+.

## Consequences

- Todas as novas features permanecem atrás de flags modulares em `config.xml` / `config.json`, mas o design da semana 1 não fica bloqueado pelo limite de tamanho ou convenções de PR do upstream.
- O Top-Shelf nunca depende de chamadas de rede, mantendo latência zero e privacidade total; conteúdos por app dependem exclusivamente de ativos locais ou metadados já presentes nos pacotes instalados.
- A navegação por D-pad preserva orçamento de 60 fps mesmo em painel 4K, evitando quedas de frame por blur dinâmico por frame.
- `PRODUCT.md` e `TVOS-SKIN.md` devem ser atualizados ao fim da etapa de desenho (`shape`) para refletir o escopo híbrido de `RenderEffect` e o Control Center.
