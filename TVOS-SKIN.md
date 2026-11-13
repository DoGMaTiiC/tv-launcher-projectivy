# TVOS-SKIN — brief do fork

Objetivo: elevar a fidelidade ao tvOS do launcher do CleanYourTV, sem perder o que ele ja faz bem
(vidro unico que desliza com spring, fundo escuro, topo limpo).

Alvo: TCL 65C6K (G08, MediaTek MT9689, Android 14 / Google TV 14, 2,4 GB RAM, painel 4K).

## Nao-objetivo

- Nao mexer em `examples/`, `tools/disable.sh`, listas de debloat, `PROMPT.md`.
- Nao traduzir. Traducao e outro fork/PR.
- Nao trocar a arquitetura (Views + Java). Nada de Compose, nada de dependencia nova.

## Restricoes tecnicas

- Fork **personal-first** e **100% offline** (sem permissao `INTERNET`), com vocabulario canonico em `GLOSSARY.md` e decisoes arquiteturais em `docs/adr/`.
- `compileSdk 34`, `targetSdk 34`, `minSdk 26`, Gradle 8.9, JDK 17.
- **Politica hibrida de Liquid Glass (ADR-0001):** sem blur dinamico por frame no que se move com o D-pad (`Focus Slab` e `Glass Dock` usam crop pre-borrado do wallpaper em bitmap cacheado de baixa resolucao + otica especular); `RenderEffect` nativo (API 31+) pode ser avaliado apenas em overlays estaticos como o `Control Center`.
- `GlassView` (`Focus Slab`) ja usa `LAYER_TYPE_HARDWARE` porque o path de clip/glow e software-rendered no
  MT5895. Qualquer efeito novo tem que caber no mesmo orcamento de frame (60 fps em 4K).
- Toda feature atras de flag em `launcher/app/src/main/res/values/config.xml`, **default off**:
  `tile_tilt`, `dock_blur`, `hero_art`, `control_center`, `music_pill`.

## Pontos de entrada no codigo

| Arquivo | Papel |
|---|---|
| `GlassView.java` | slab de vidro que segue o foco (rim, sheen, glow, estilos GLASS_*) |
| `Motion.java` | springs e modo de movimento reduzido |
| `DockSlots.java`, `DockSlotsTest` | posicoes do dock |
| `DockCorners.java` equivalente em `DockCornersTest` | raio de canto do dock |
| `HeroArt.java`, `TopFade.java` | arte/fade do topo |
| `WallpaperPlayer.java`, `ClockDream.java` | wallpaper em video e screensaver |
| `Config.java`, `ThemeProvider.java` | flags e temas |

## Escopo por prioridade

### Semana 1 (Base pronta para a chegada da TCL 65C6K)
1. **tile_tilt (`Focus Slab` + Parallax)** — tile focado escala ~1.05 e inclina 2–4° seguindo a direcao do D-pad com retorno via spring, brilho especular que anda junto e parallax interno de 2 camadas (banner a 1.035x). Cards em vidro fume para apps sem banner 16:9.
   Teste: `MotionTest` + um novo `TileTiltTest` (matematica de eixo/limite, sem Android).
2. **dock_blur (`Glass Dock`)** — crop pre-borrado do wallpaper atras do dock, com saturacao levemente elevada e borda superior iluminada (`1.5px`). Labels no dock so ao focar (titulo grande no topo); na grade abaixo, labels visiveis com opacidade reduzida fora do foco.
   Teste: `DockOpacityTest`/`GlassStyleTest` estendidos.
3. **hero_art (`Hero Artwork`)** — imagem por app em `hero/<package>.jpg` na pasta externa da TV, crossfade ao focar, vinheta escura no rodape e fallback automatico elegante para o wallpaper 4K.
4. **control_center (`Control Center` + Top Bar Liquid Glass)** — barra superior limpa em `Outfit` e painel overlay em vidro acionado por `Up` ou botao Settings, com cartao **Now Playing** (`MediaSession`) e 5 atalhos (Entradas HDMI/TV, Wi-Fi/Rede, Imagem/Som, `Aerial Screensaver` em fotos 4K e Configuracoes do Launcher).
5. **Skill & Scripts de Midia 4K (`tv-media-curator`)** — skill local e utilitarios em `tools/` para curar, recortar/otimizar em 4K e enviar wallpapers, `hero/<package>.jpg` e videos em loop para a TV.

### Fase 2 (Pos-base)
6. **music_pill (`Music Pill` + Audio Background sobre Apps/HDMI)** — capsula flutuante em Liquid Glass (`SYSTEM_ALERT_WINDOW`) ancorada em canto configuravel sobre qualquer app ou HDMI (ex.: PS5 / jogo mudo via `appops`), expansivel para controles completos ou tela cheia, pareada com player de YouTube Music em background (`handleAudioFocus = false`).
7. **Video MP4 em loop no `Aerial Screensaver`** — suporte a videos locais silenciosos em loop no `ClockDream` / `ClockScene`.

## Definicao de pronto (por feature)

- flag em `config.xml`, default off, documentada no `Config.java`;
- teste unitario rodando em `./gradlew test` (sem emulador);
- nenhum frame descartado perceptivel no D-pad segurado (validar na TV);
- screenshot antes/depois em `docs/tvos-skin/`.

## Build e teste

```bash
cd launcher
./gradlew test          # unit tests, sem TV
./gradlew assembleDebug # APK
```

Instalar na TV (quando ela chegar): `TV_HOST=<ip> launcher/build-and-install.sh`
(exige `tools/venv` com `requirements.txt`; JDK 17; Android SDK 34 + build-tools 34).

## Regras de convivencia com upstream

- `origin` = Gathin23/cleanyourtv, branch de trabalho `tvos-skin`.
- Mudancas pequenas, em commits separados por feature, para virar PR depois.
- Nada de reformatacao de codigo alheio. Diff cirurgico.
