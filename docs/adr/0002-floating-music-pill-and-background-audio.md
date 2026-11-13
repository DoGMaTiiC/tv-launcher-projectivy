# 0002. Music Pill flutuante em Liquid Glass e reprodução em segundo plano sobre Apps/HDMI

Status: accepted

Decidimos projetar o recurso **Music Pill** como uma janela de sobreposição do sistema (`TYPE_APPLICATION_OVERLAY` via `SYSTEM_ALERT_WINDOW`, concedida via ADB) combinada com controle de `AudioFocus` (`appops TAKE_AUDIO_FOCUS ignore` / `PLAY_AUDIO ignore` ou player de música com `handleAudioFocus = false`), permitindo que músicas do YouTube Music toquem em segundo plano enquanto jogos no PS5 (HDMI) ou partidas de basquete rodam na tela.

## Considered Options

- **Onde exibir a música em reprodução:** Apenas dentro da Home/Control Center vs. janela Picture-in-Picture (PiP) padrão do Android vs. **Music Pill** customizada em Liquid Glass ancorada num canto configurável da tela (`SYSTEM_ALERT_WINDOW`).
- **Como manter o áudio ativo sobre outros apps/HDMI:** Depender do app oficial do YouTube Music para TV (que encerra ao perder foco) vs. usar/adaptar um cliente compatível com serviço em segundo plano (`MediaSessionService` + `handleAudioFocus = false`) aliado a regras de `appops` por pacote para silenciar/ignorar foco do app de vídeo quando desejado.

## Consequences

- No modo compacto, a **Music Pill** usa `FLAG_NOT_FOCUSABLE` para nunca roubar os comandos do controle remoto ou do PS5, exibindo capa, título/artista e tempo de reprodução dentro da safe area (`60px`) em qualquer um dos 4 cantos configuráveis.
- Acionar a expansão (pelo Control Center ou atalho do controle) torna a cápsula focável temporariamente para Play/Pause, pular faixa, trocar canto, abrir em tela cheia ou minimizar.
- Como a camada de UI do Android TV (`SurfaceFlinger`) fica acima do plano de hardware do `TvView` (HDMI), a **Music Pill** aparece tanto sobre apps Android quanto sobre a imagem do PS5 nas portas HDMI da TCL 65C6K.
