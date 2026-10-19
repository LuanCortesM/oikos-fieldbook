# Política de Privacidade — Oikos FieldBook

Última atualização: 19 de outubro de 2026
Responsável: Luan da Silva Cortes Maciel (MACIEL, L. S. C.)
Contato: Projeto Herpeto Mantiqueira — herpetomantiqueira@gmail.com

Versão publicada (com o mesmo texto): https://luancortesm.github.io/oikos-fieldbook/privacidade/
Página gerada de `docs/privacidade/modelo.html` por `docs/privacidade/gerar.pl`.

## Resumo

- O Oikos FieldBook não tem conta, login nem servidor próprio. O desenvolvedor
  só recebe o que você mesmo enviar a ele por e-mail.
- Tudo o que você registra (projetos, ocorrências, coordenadas, trilhas, fotos,
  áudios e anotações) fica no seu aparelho.
- Os dados só saem do aparelho quando você exporta, faz backup ou envia um
  pacote para outro celular, sempre para o destino que você escolher.
- O aplicativo usa a internet só para três coisas: o mapa online, o download
  das listas de espécies e, se você ligar essa opção, a sugestão de endereço
  pelo Google. Veja os detalhes em "Uso de internet".
- Não há publicidade, análise de uso, rastreamento nem identificador de
  anúncio.

## Dados que ficam no aparelho

Projetos, expedições, ocorrências, campos Darwin Core, coordenadas, trilhas
GPS, fotografias, gravações de áudio e anotações ficam no armazenamento interno
do aplicativo, que o Android isola dos outros aplicativos. Eles ficam guardados
até você apagá-los.

Ao apagar um projeto, uma expedição ou uma ocorrência, o item vai para a
lixeira do aplicativo e é removido de vez depois de 30 dias, ou antes, se você
esvaziar a lixeira. Desinstalar o aplicativo remove todos os dados do aparelho.

## Quando os dados saem do aparelho

Somente por ação sua:

- **Exportar**: gera planilhas, Darwin Core Archive e outros arquivos. Você
  escolhe, na tela de compartilhamento do Android, para onde enviar (e-mail,
  Google Drive, WhatsApp, computador etc.).
- **Backup do aplicativo**: gera um arquivo .zip com o banco de dados, as fotos
  e os áudios e salva onde você escolher.
- **Enviar para outro celular, como arquivo**: gera um pacote para juntar
  projetos em outro aparelho com o Oikos FieldBook. Você escolhe por onde
  mandar (WhatsApp, e-mail, Google Drive, cabo etc.).
- **Enviar direto para o celular ao lado**: o pacote vai de um aparelho ao
  outro por Bluetooth e Wi-Fi, sem internet, pela conexão por proximidade do
  Google Play Services (Nearby Connections), que é criptografada. O envio só
  acontece depois que as duas pessoas conferem o mesmo código de 4 dígitos e
  quem recebe toca em Aceitar. Enquanto a tela de receber está aberta, o
  celular aparece para quem usa o Oikos FieldBook por perto com o nome do
  aparelho; quem envia aparece com o nome do coletor do projeto ou, se não
  houver, com o nome do aparelho.

Esses arquivos e pacotes podem conter coordenadas e fotos. Depois de enviados,
ficam sob as regras do serviço ou do aparelho que os recebeu.

## Permissões

- **Localização (precisa e aproximada)**: registrar a coordenada da ocorrência
  e os pontos da trilha. Usada só quando você captura uma localização ou mantém
  uma trilha ativa.
- **Localização em primeiro plano, notificações e manter o aparelho ativo**:
  continuar gravando a trilha com a tela apagada. Enquanto isso, o Android
  mostra um aviso fixo na barra de notificações.
- **Câmera**: fotografar o registro.
- **Microfone**: gravar áudio (vocalizações, observação narrada).
- **Dispositivos por perto, Bluetooth e Wi-Fi**: encontrar o celular ao lado e
  enviar ou receber um pacote direto, sem internet. Usadas só enquanto a tela
  de enviar ou de receber está aberta. No Android 11 e anteriores, o sistema
  exige a permissão de localização para procurar aparelhos por Bluetooth; nesse
  uso o Oikos não registra a localização.
- **Internet e estado da rede**: os usos descritos em "Uso de internet".

O aplicativo não pede acesso a contatos, agenda, SMS, chamadas nem a outros
arquivos do aparelho. O que você importa (fotos, áudios, trilhas, mapas
offline, backups) é escolhido por você, arquivo por arquivo, no seletor do
Android.

## Uso de internet

O município e o estado de uma coordenada no Brasil são calculados no próprio
aparelho, sem internet, com a Malha Municipal 2022 do IBGE, que vem dentro do
aplicativo. As conexões acontecem em três casos:

1. **Mapa online.** Ao abrir um mapa sem ter escolhido um mapa offline, o
   aplicativo baixa as imagens do mapa do OpenStreetMap. O servidor recebe a
   área visualizada e o endereço IP do aparelho, como em qualquer aplicativo de
   mapas, e segue a política de privacidade da OpenStreetMap Foundation
   (https://osmfoundation.org/wiki/Privacy_Policy). Com um mapa offline (arquivo
   MBTiles importado em Configurações), nenhuma imagem é pedida à internet.
2. **Listas de espécies.** Ao baixar uma lista (Flora e Funga do Brasil,
   Catálogo Taxonômico da Fauna do Brasil) ou abrir a tela de listas, que
   confere se há versão nova, o aplicativo acessa o IPT do Jardim Botânico do
   Rio de Janeiro ou, se o IPT recusar a conexão, o ChecklistBank (Catalogue of
   Life/GBIF). Esses pedidos não levam coordenadas nem registros; o servidor
   recebe apenas o endereço IP do aparelho.
3. **Sugestão de endereço pelo Google (opcional, desligada por padrão).** Se
   você ligar "Sugerir endereço pelo Google" em Configurações, a coordenada
   capturada é enviada ao serviço de endereços do Google (Geocoder do Android)
   para preencher município, estado e país. A coordenada vai só ao Google, só
   para obter o endereço, e o tratamento por ele segue a política de
   privacidade do Google (https://policies.google.com/privacy). Com a opção
   desligada, nada é enviado.

Nenhum registro, fotografia, áudio, anotação ou dado de identificação é
enviado em nenhum desses casos. O envio direto para o celular ao lado não passa
pela internet.

**Se o local de coleta for sensível** (espécie ameaçada, população pequena,
área sob pressão de coleta ilegal), use um mapa offline e mantenha a sugestão
de endereço pelo Google desligada. A coleta e o salvamento dos registros
funcionam normalmente sem internet.

## Relatar problema ou dar ideia

O botão "Relatar problema ou dar ideia" abre o seu aplicativo de e-mail com
uma mensagem pronta para herpetomantiqueira@gmail.com. Ela leva o que você
escreveu e, para ajudar a encontrar o problema, a versão do aplicativo e do
Android, o modelo do aparelho e o idioma do aplicativo. Nenhum registro,
coordenada, foto ou áudio vai junto, e nada é enviado se você não tocar em
enviar no seu e-mail.

A mensagem chega ao desenvolvedor com o seu endereço de e-mail. Ela é usada só
para responder e melhorar o aplicativo, não é compartilhada com ninguém e é
apagada quando deixa de ser necessária. Você pode pedir a exclusão a qualquer
momento pelo mesmo e-mail.

## Segurança

- **No aparelho**: os dados ficam no armazenamento interno do aplicativo, que o
  Android isola dos outros aplicativos.
- **Nas conexões**: o mapa online, as listas de espécies e a sugestão de
  endereço pelo Google usam conexões criptografadas (HTTPS). O envio direto
  entre celulares também é criptografado.
- **Nos arquivos que você gera**: exportações, backups e pacotes ficam sob a
  sua guarda: guarde-os em local seguro, principalmente se tiverem coordenadas
  de espécies sensíveis.

## Backup do sistema Android

Se o backup automático da conta Google estiver ligado no aparelho, o Android
inclui nele o banco de dados dos registros e as configurações do Oikos. Fotos,
áudios, mapas offline e listas de espécies ficam de fora, por causa do limite
de 25 MB por aplicativo, e são cobertos pelo backup do próprio aplicativo. Na
transferência direta para um celular novo, o Android pode copiar também as
fotos e os áudios. Esse backup e essa transferência são feitos pelo sistema,
ficam na conta Google do próprio usuário e o desenvolvedor não tem acesso a
eles. Para desligar, use as configurações de backup do Android.

## Seus dados, suas decisões

Como os dados ficam só no seu aparelho, você mesmo pode consultá-los, corrigir,
exportar ou apagar a qualquer momento, dentro do aplicativo. O desenvolvedor
não tem cópia deles e, por isso, não consegue recuperá-los se forem apagados
ou se o aparelho for perdido: faça backups.

## Menores de idade

O aplicativo é voltado a pesquisadores, estudantes, monitores ambientais e
demais pessoas em atividades de campo, e não é direcionado a crianças.

## Alterações e contato

Mudanças nesta política são publicadas junto com a atualização do aplicativo,
com a data no topo desta página. Dúvidas ou pedidos sobre privacidade: Projeto
Herpeto Mantiqueira, herpetomantiqueira@gmail.com.
