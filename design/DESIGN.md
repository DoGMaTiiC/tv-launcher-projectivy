---
version: alpha
name: Living Room Focus
description: Uma linguagem de tela para 3 metros de distancia, derivada dos principios publicos do tvOS (HIG) e implementada em Views Android.
colors:
  primary: "#F5F5F7"
  background: "#000000"
  surface: "#0B0B0C"
  surface-elevated: "#161618"
  on-surface: "#F5F5F7"
  on-surface-secondary: "#A1A1A6"
  focus-glow: "#FFFFFF"
  glass-fill: "rgba(255,255,255,0.08)"
  accent: "#0A84FF"
typography:
  display:
    fontFamily: Outfit
    fontSize: 64px
    fontWeight: 600
    lineHeight: 1.05
    letterSpacing: "-0.02em"
  title:
    fontFamily: Outfit
    fontSize: 48px
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-0.01em"
  headline:
    fontFamily: Outfit
    fontSize: 38px
    fontWeight: 500
    lineHeight: 1.15
  body:
    fontFamily: Outfit
    fontSize: 29px
    fontWeight: 400
    lineHeight: 1.35
  label:
    fontFamily: Outfit
    fontSize: 25px
    fontWeight: 500
    lineHeight: 1.2
rounded:
  sm: 8px
  md: 12px
  lg: 20px
  xl: 28px
  full: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  safe: 60px
components:
  tile:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.lg}"
    width: 320px
    height: 180px
  tile-focus:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.lg}"
    width: 336px
    height: 189px
  focus-glass:
    backgroundColor: "{colors.glass-fill}"
    rounded: "{rounded.xl}"
  dock:
    backgroundColor: "{colors.glass-fill}"
    rounded: "{rounded.xl}"
    padding: "{spacing.lg}"
  tile-label:
    textColor: "{colors.on-surface}"
    typography: "{typography.label}"
  tile-label-dim:
    textColor: "{colors.on-surface-secondary}"
    typography: "{typography.label}"
  focus-ring:
    backgroundColor: "{colors.focus-glow}"
    rounded: "{rounded.full}"
    size: 8px
  pill-active:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.background}"
    rounded: "{rounded.full}"
    padding: "{spacing.md}"
---

## Overview

A tela e vista a 2,5-3,5 m. Isso decide tudo: menos elementos, tipografia maior, contraste alto e movimento que confirma o foco em vez de decorar. A referencia e o tvOS, mas a implementacao e Android Views (Java), sem Compose e sem biblioteca de design nova.

Principio central: **o foco e o produto**. Todo o resto (wallpaper, dock, grade) existe para deixar claro onde o foco esta e para onde ele vai.

## Colors

- **Background (#000000):** preto real; em painel mini-LED e o que da contraste sem halo.
- **Surface (#0B0B0C) / Surface elevated (#161618):** degraus casi invisiveis para separar camadas sem borda.
- **On-surface (#F5F5F7):** texto principal; nunca branco puro em bloco grande.
- **On-surface secondary (#A1A1A6):** metadados e labels sem foco.
- **Focus glow (#FFFFFF):** a luz do item focado. E o unico branco que pode ser duro.
- **Glass fill (rgba 8%):** dock e slab; nunca opaco.
- **Accent (#0A84FF):** so para acao interativa real; nao usar como decoracao.

## Typography

Outfit (ja no repo, OFL) para tudo: geometrica, boa a distancia, e a mesma familia em titulo e label mantem a tela calma. Piso de 25px para label e 29px para corpo — abaixo disso nao se le do sofa. Titulos 48px, display 64px. Peso minimo 400; nada de hairline, que desaparece com compressao e motion blur.

## Layout

Grade de 60px de safe area nas quatro bordas (overscan de TV). Uma linha de 5-7 tiles e melhor que uma grade densa. Espacamento em multiplos de 4, com 24px entre tiles e 48px entre secoes. Nada de texto corrido: titulo + uma linha de metadado.

## Elevation & Depth

Duas camadas de profundidade, nunca mais:

1. **Dock/slab de vidro** — 8% de branco, com rim de 1.5px e glow externo fraco.
2. **Tile focado** — escala 1.05, sombra suave e tilt de 2-4 graus seguindo o D-pad.

O parallax do tvOS tem tres camadas (fundo 1-2pt, meio 3-5pt, frente 5-8pt). Aqui fazemos no maximo duas: arte e label deslocam juntas; o fundo fica parado. Sem blur por frame.

## Shapes

Squircle, nao retangulo arredondado: superelipse com expoente ~4 para tiles e dock. Raio 20px no tile de 320x180, 28px no dock. Icone de app em 16:9, nao em quadrado.

## Components

- **tile** — repouso 320x180, arte 16:9, sem label visivel.
- **tile-focus** — 336x189 (1.05), label aparece abaixo, glow branco.
- **focus-glass** — a unica peca que se move entre tiles; ela e o foco, nao o tile.
- **dock** — 4 a 5 itens, centrado, vidro 8%, labels so no foco.
- **tile-label** — 25px, peso 500, cor on-surface; dim fora do foco.

## Do's and Don'ts

- **Do** manter o foco sempre visivel e sempre restauravel ao voltar para uma tela.
- **Do** usar movimento para confirmar foco, nao para enfeitar.
- **Do** testar tudo a 3 metros com o controle real na mao.
- **Don't** usar fundo claro, gradiente colorido ou sombra dura.
- **Don't** colocar mais de duas linhas de texto por tile.
- **Don't** animar blur, nem usar RenderEffect por frame.
- **Don't** trocar a ordem das linhas entre visitas: isso obriga a reescanear a tela.
