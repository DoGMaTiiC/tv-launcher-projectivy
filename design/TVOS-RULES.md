# Regras tvOS uteis para o fork

Destilado de Apple HIG (tvOS) e de `platform-design-skills/skills/tvos`
(MIT, autor platform-design-skills), com os numeros que importam para uma
implementacao Android. Nao e copia: e a lista de decisao.

## Foco

- Todo elemento interativo tem estado de foco inequivoco: escala 1.05-1.1,
  sombra, brilho ou borda. Nunca so cor.
- Movimento de foco previsivel na direcao espacial do D-pad.
- Um elemento ja focado ao abrir a tela (o de maior intencao).
- Memoria de foco ao voltar para uma tela.
- Nunca prender o foco: sempre existe saida para todos os lados.
- Parallax: fundo 1-2pt, meio 3-5pt, frente 5-8pt. Aqui: no maximo 2 camadas.

## Controle remoto

- Menu/Back sempre volta; nunca sequestrar para outra acao.
- Play/Pause controla midia em qualquer tela.
- Nada de gesto multi-dedo, pinça ou long-press como caminho unico.
- Feedback imediato ao clicar.

## 10-foot UI

| Elemento | Minimo | Recomendado |
|---|---|---|
| Corpo | 29pt | 31-35 |
| Label secundario | 25pt | 29 |
| Titulo | 48pt | 52-76 |
| Header | 64pt | 76-96 |
| Botao | 29pt | 35-38 |

- Safe area: 60pt de insercao nas bordas (overscan).
- Contraste AA: 4.5:1 corpo, 3:1 texto grande.
- Sem fonte fina nem borda hairline.
- Pouco texto por tela; paragrafo so em overlay sob demanda.

## Alvos

- Card minimo 250x150pt.
- Uma linha de 5-7 cards e melhor que grade de 20+.
- Peso minimo de fonte: medium/semibold em titulo.

## Anti-padroes

- Tab bar embaixo (padrao iOS, errado em TV).
- Menu hamburguer, pull-to-refresh, pinch-to-zoom.
- Toast: some na distancia. Use alerta modal ou banner persistente.
- Barra de rolagem fina: use "peek" do proximo item.
- Formulario longo digitado no controle.

## Como isso vira codigo aqui

- `GlassView.java` ja e o slab de foco: ganha tilt/escala.
- `Motion.java` ja e o spring: ganha rigidez por interacao.
- Tokens em `design/DESIGN.md`; exportar para `res/values/` quando implementar.
